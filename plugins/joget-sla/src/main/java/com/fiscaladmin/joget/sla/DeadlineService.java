package com.fiscaladmin.joget.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * SLA clock engine. Clocks are deadline rows created from {@link SlaConfig} SLA
 * definitions; {@link #sweep} recomputes statuses, pauses/resumes on hold, and
 * escalates — idempotently via {@code escalationLevel} (breach escalation fired
 * exactly once). All lifecycle events are appended to the case event chain.
 *
 * <p>Project-neutral: the deadline/case carrier-table ids, the supervisor role that
 * {@code "supervisor"} in an escalation chain resolves to, and the optional extra
 * escalation payload fields (e.g. a subject name + amount) are all configurable via
 * settable statics or the full constructor. The case row is assumed to carry the
 * generic envelope fields {@code caseType, currentState, slaStatus, assignee,
 * priority, caseRef}.</p>
 */
public class DeadlineService {

    public static final String DEFAULT_DEADLINE_FORM = "caseDeadline";
    public static final String DEFAULT_CASE_FORM = "case";
    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static volatile String defaultDeadlineFormId = DEFAULT_DEADLINE_FORM;
    private static volatile String defaultCaseFormId = DEFAULT_CASE_FORM;
    private static volatile String supervisorRole = "supervisor";
    private static volatile String taxpayerField = null;   // omitted from payload when null/blank
    private static volatile String amountField = null;

    /** Point the engine at a consumer's deadline + case tables (call once at start-up). */
    public static void setFormIds(String deadlineFormId, String caseFormId) {
        if (deadlineFormId != null && !deadlineFormId.trim().isEmpty()) defaultDeadlineFormId = deadlineFormId.trim();
        if (caseFormId != null && !caseFormId.trim().isEmpty()) defaultCaseFormId = caseFormId.trim();
    }

    /** The role that {@code "supervisor"} in an escalation notify-chain resolves to. */
    public static void setSupervisorRole(String role) {
        if (role != null && !role.trim().isEmpty()) supervisorRole = role.trim();
    }

    /** Optional case-row fields added to the escalation event payload (null = omit). */
    public static void setEscalationPayloadFields(String taxpayer, String amount) {
        taxpayerField = taxpayer;
        amountField = amount;
    }

    private final FormDataDao dao;
    private final SlaConfig cfg;
    private final CaseEventWriter events;
    private final String fDeadline;
    private final String fCase;

    public DeadlineService(FormDataDao dao, SlaConfig cfg, CaseEventWriter events) {
        this(dao, cfg, events, defaultDeadlineFormId, defaultCaseFormId);
    }

    public DeadlineService(FormDataDao dao, SlaConfig cfg, CaseEventWriter events,
                           String deadlineFormId, String caseFormId) {
        this.dao = dao;
        this.cfg = cfg;
        this.events = events;
        this.fDeadline = deadlineFormId;
        this.fCase = caseFormId;
    }

    /** START: one clock per SLA row of the case type. Returns clocks created. */
    public int start(String caseId, String actor, LocalDateTime now) {
        FormRow c = dao.load(fCase, fCase, caseId);
        if (c == null) {
            throw new IllegalStateException("case row not found: " + caseId);
        }
        String type = prop(c, "caseType");
        FormRowSet slas = cfg.slaRows(type);
        int created = 0;
        if (slas != null) {
            for (FormRow sla : slas) {
                String clockCode = prop(sla, "clockCode");
                if (hasOpenClock(caseId, clockCode)) {
                    continue; // idempotent (re-open keeps a single open clock)
                }
                long days = parseLong(prop(sla, "durationDays"), 30);
                LocalDateTime due = addDays(now, days, cfg.calendarRow(prop(sla, "calendar")));
                long span = Duration.between(now, due).toMinutes();
                long warnPct = parseLong(prop(sla, "warnPct"), 75);
                long critPct = parseLong(prop(sla, "critPct"), 90);
                FormRow d = new FormRow();
                d.setId(UUID.randomUUID().toString());
                d.setProperty("caseId", caseId);
                d.setProperty("clockCode", clockCode);
                d.setProperty("startedAt", ISO.format(now));
                d.setProperty("dueAt", ISO.format(due));
                d.setProperty("warnAt", ISO.format(now.plusMinutes(span * warnPct / 100)));
                d.setProperty("critAt", ISO.format(now.plusMinutes(span * critPct / 100)));
                d.setProperty("status", "RUNNING");
                d.setProperty("pausedDays", "0");
                d.setProperty("escalationLevel", "0");
                save(fDeadline, d);
                created++;
            }
        }
        if (created > 0) {
            c.setProperty("slaStatus", "GREEN");
            save(fCase, c);
        }
        return created;
    }

    /** CLOSE: open clocks of the case -> MET; current slaStatus cleared. */
    public int close(String caseId, String actor) {
        int n = 0;
        for (FormRow d : openClocks(caseId)) {
            d.setProperty("status", "MET");
            save(fDeadline, d);
            n++;
        }
        FormRow c = dao.load(fCase, fCase, caseId);
        if (c != null) {
            c.setProperty("slaStatus", "-");
            save(fCase, c);
        }
        return n;
    }

    /** SWEEP: full pass over open clocks as of the given time. Returns summary. */
    public String sweep(String actor, LocalDateTime asOf) {
        FormRowSet clocks = dao.find(fDeadline, fDeadline,
                "WHERE e.customProperties.status = ?1 OR e.customProperties.status = ?2",
                new Object[]{"RUNNING", "PAUSED"}, "dateCreated", Boolean.FALSE, null, null);
        Set<String> terminals = cfg.allTerminalCodes();
        Map<String, Integer> caseWorst = new HashMap<String, Integer>();
        Map<String, FormRow> caseRows = new HashMap<String, FormRow>();
        int swept = 0, paused = 0, resumed = 0, escalated = 0, breached = 0, met = 0;

        if (clocks != null) {
            for (FormRow d : clocks) {
                String caseId = prop(d, "caseId");
                FormRow c = caseRows.computeIfAbsent(caseId,
                        id -> dao.load(fCase, fCase, id));
                if (c == null) {
                    continue;
                }
                swept++;
                String type = prop(c, "caseType");
                String state = prop(c, "currentState");
                if (terminals.contains(state)) {
                    d.setProperty("status", "MET");
                    save(fDeadline, d);
                    met++;
                    continue;
                }
                FormRow sla = cfg.slaRow(type, prop(d, "clockCode"));
                boolean pauseOnHold = sla == null
                        || !"".equals(prop(sla, "pauseOnHold")); // default true
                boolean onHold = "OnHold".equals(cfg.envelopeOf(type, state));
                String status = prop(d, "status");

                if ("PAUSED".equals(status) && !onHold) { // resume, extend by pause
                    LocalDateTime pausedAt = parse(prop(d, "pausedAt"), asOf);
                    Duration pd = Duration.between(pausedAt, asOf);
                    shift(d, "dueAt", pd);
                    shift(d, "warnAt", pd);
                    shift(d, "critAt", pd);
                    d.setProperty("pausedDays", String.valueOf(
                            parseLong(prop(d, "pausedDays"), 0) + Math.max(0, pd.toDays())));
                    d.setProperty("status", "RUNNING");
                    d.setProperty("pausedAt", "");
                    status = "RUNNING";
                    resumed++;
                    events.append(caseId, "SLA_RESUMED", actor, "", "",
                            "clock " + prop(d, "clockCode") + " resumed", null);
                } else if ("RUNNING".equals(status) && onHold && pauseOnHold) {
                    d.setProperty("status", "PAUSED");
                    d.setProperty("pausedAt", ISO.format(asOf));
                    status = "PAUSED";
                    paused++;
                    events.append(caseId, "SLA_PAUSED", actor, "", "",
                            "clock " + prop(d, "clockCode") + " paused (on hold)", null);
                }

                LocalDateTime dueAt = parse(prop(d, "dueAt"), asOf.plusDays(1));
                LocalDateTime critAt = parse(prop(d, "critAt"), dueAt);
                LocalDateTime warnAt = parse(prop(d, "warnAt"), critAt);
                LocalDateTime ref = "PAUSED".equals(status)
                        ? parse(prop(d, "pausedAt"), asOf) : asOf; // frozen while paused
                String chain = sla == null ? "" : prop(sla, "escalationChain");
                long maxLevels = jsonInt(chain, "maxLevels", 3);
                long level = parseLong(prop(d, "escalationLevel"), 0);

                if ("RUNNING".equals(status)) {
                    if (!ref.isBefore(dueAt) && level < 2 && maxLevels >= 2) {
                        d.setProperty("status", "BREACHED");
                        d.setProperty("escalationLevel", "2");
                        escalate(c, d, sla, 2, "SLA_BREACHED", actor, ref, dueAt);
                        breached++;
                        escalated++;
                    } else if (!ref.isBefore(dueAt)) {
                        d.setProperty("status", "BREACHED");
                    } else if (!ref.isBefore(critAt) && level < 1 && maxLevels >= 1) {
                        d.setProperty("escalationLevel", "1");
                        escalate(c, d, sla, 1, "SLA_ESCALATED", actor, ref, dueAt);
                        escalated++;
                    }
                }
                d.setProperty("lastSweepAt", ISO.format(asOf));
                save(fDeadline, d);

                int lvl = "BREACHED".equals(prop(d, "status")) || !ref.isBefore(critAt) ? 2
                        : !ref.isBefore(warnAt) ? 1 : 0;
                caseWorst.merge(caseId, lvl, Math::max);
            }
        }
        for (Map.Entry<String, Integer> e : caseWorst.entrySet()) {
            FormRow c = caseRows.get(e.getKey());
            String s = e.getValue() == 2 ? "RED" : e.getValue() == 1 ? "AMBER" : "GREEN";
            if (!s.equals(prop(c, "slaStatus"))) {
                c.setProperty("slaStatus", s);
                save(fCase, c);
            }
        }
        return "swept=" + swept + " paused=" + paused + " resumed=" + resumed
                + " escalated=" + escalated + " breached=" + breached + " met=" + met;
    }

    private void escalate(FormRow c, FormRow d, FormRow sla, int level,
                          String eventType, String actor, LocalDateTime ref, LocalDateTime dueAt) {
        String caseId = c.getId();
        long daysOverdue = Math.max(0, Duration.between(dueAt, ref).toDays());
        String chain = sla == null ? "" : prop(sla, "escalationChain");
        StringBuilder eb = new StringBuilder();
        eb.append("\"caseRef\":\"").append(CaseEventWriter.esc(prop(c, "caseRef"))).append("\"");
        if (taxpayerField != null && !taxpayerField.isEmpty()) {
            eb.append(",\"taxpayer\":\"").append(CaseEventWriter.esc(prop(c, taxpayerField))).append("\"");
        }
        if (amountField != null && !amountField.isEmpty()) {
            eb.append(",\"amount\":\"").append(CaseEventWriter.esc(prop(c, amountField))).append("\"");
        }
        eb.append(",\"slaStatus\":\"RED\",\"daysOverdue\":").append(daysOverdue)
                .append(",\"clockCode\":\"").append(CaseEventWriter.esc(prop(d, "clockCode")))
                .append("\",\"level\":").append(level);
        String extra = eb.toString();
        events.append(caseId, eventType, actor, "", "",
                "SLA escalation level " + level, extra);
        List<String> notify = jsonList(chain, "notify");
        if (notify.isEmpty()) {
            notify = List.of("assignee", "supervisor"); // default
        }
        for (String who : notify) {
            String recipient = "assignee".equals(who) ? prop(c, "assignee")
                    : "supervisor".equals(who) ? supervisorRole : who;
            events.append(caseId, "NOTIF_PENDING", actor, "", "",
                    "SLA escalation notice", "\"recipient\":\""
                            + CaseEventWriter.esc(recipient) + "\"," + extra);
        }
        if (!chain.contains("\"bumpPriority\":false")) { // default true
            long pr = parseLong(prop(c, "priority"), 0) + 1;
            c.setProperty("priority", String.valueOf(pr));
            save(fCase, c);
        }
        String reassignTo = jsonStr(chain, "reassignTo");
        if (!reassignTo.isEmpty()) {
            String from = prop(c, "assignee");
            c.setProperty("assignee", reassignTo);
            save(fCase, c);
            events.append(caseId, "CASE_REASSIGNED", actor, "", "",
                    "SLA escalation reassignment", "\"from\":\"" + CaseEventWriter.esc(from)
                            + "\",\"to\":\"" + CaseEventWriter.esc(reassignTo) + "\"");
        }
    }

    // ---------- helpers ----------

    /** durationDays added per calendar: WORKING skips Sat/Sun + holiday CSV. */
    public static LocalDateTime addDays(LocalDateTime start, long days, FormRow calendar) {
        boolean working = calendar != null
                && "WORKING".equals(nz(calendar.getProperty("workingDayMode")));
        if (!working) {
            return start.plusDays(days);
        }
        Set<LocalDate> holidays = new HashSet<LocalDate>();
        for (String h : nz(calendar.getProperty("holidays")).split("[,;\\s]+")) {
            try {
                holidays.add(LocalDate.parse(h.trim()));
            } catch (Exception ignored) { /* tolerate blanks */ }
        }
        LocalDateTime d = start;
        long added = 0;
        while (added < days) {
            d = d.plusDays(1);
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
                    || holidays.contains(d.toLocalDate())) {
                continue;
            }
            added++;
        }
        return d;
    }

    private boolean hasOpenClock(String caseId, String clockCode) {
        for (FormRow d : openClocks(caseId)) {
            if (clockCode.equals(prop(d, "clockCode"))) {
                return true;
            }
        }
        return false;
    }

    private List<FormRow> openClocks(String caseId) {
        FormRowSet rows = dao.find(fDeadline, fDeadline,
                "WHERE e.customProperties.caseId = ?1 AND (e.customProperties.status = ?2"
                        + " OR e.customProperties.status = ?3)",
                new Object[]{caseId, "RUNNING", "PAUSED"},
                "dateCreated", Boolean.FALSE, null, null);
        return rows == null ? new ArrayList<FormRow>() : rows;
    }

    private void shift(FormRow d, String field, Duration by) {
        LocalDateTime t = parse(prop(d, field), null);
        if (t != null) {
            d.setProperty(field, ISO.format(t.plus(by)));
        }
    }

    static LocalDateTime parse(String s, LocalDateTime dflt) {
        try {
            return LocalDateTime.parse(s, ISO);
        } catch (Exception e) {
            return dflt;
        }
    }

    static long parseLong(String s, long dflt) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    static int jsonInt(String json, String key, int dflt) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(nz(json));
        return m.find() ? Integer.parseInt(m.group(1)) : dflt;
    }

    static List<String> jsonList(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(nz(json));
        List<String> out = new ArrayList<String>();
        if (m.find()) {
            for (String item : m.group(1).split(",")) {
                String v = item.trim().replaceAll("^\"|\"$", "");
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    static String jsonStr(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(nz(json));
        return m.find() ? m.group(1) : "";
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String prop(FormRow r, String id) {
        String v = r == null ? null : r.getProperty(id);
        return v == null ? "" : v.trim();
    }

    private void save(String form, FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, form, set);
    }
}
