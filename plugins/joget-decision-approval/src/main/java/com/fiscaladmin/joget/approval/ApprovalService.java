package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;

/**
 * ApprovalService — configurable decision &amp; approval router.
 *
 * <p>Mechanism only (the meaning is config): a consumer lifecycle <b>requests</b> approval for an
 * action; the service resolves a <b>route</b> from the unified authority matrix {@code mmAuthority},
 * and either lets the gate open immediately (no band matches the materiality — no approval required)
 * or raises a {@code Pending} approval record. Authorities then <b>decide</b> (approve / reject /
 * return) with a <b>mandatory reason</b>.
 *
 * <p>Routing topologies (from the band's {@code bodyType} + {@code level} + {@code quorum}):
 * <ul>
 *   <li><b>SINGLE</b> — one decision by an approver ranked ≥ the band's {@code level}.</li>
 *   <li><b>CHAIN</b> ({@code bodyType=CHAIN}, {@code level} = ordered CSV of levels) — sequential
 *       sign-off; each step needs an approver ranked ≥ that step's level; the effect runs only
 *       after the last step.</li>
 *   <li><b>QUORUM</b> ({@code bodyType=COLLEGIAL}, {@code quorum=N}) — N <b>distinct</b> approvers
 *       each ranked ≥ {@code level}; a repeat voter is ignored; the effect runs at the Nth.</li>
 * </ul>
 *
 * <p>Invariants across every topology: rank gate (reusing {@link DecisionService#rank}); four-eyes
 * separation-of-duties (no approver is the requester; no approver votes twice); reject at any step
 * rejects the whole request; the lifecycle is guarded + audited by {@link StatusManager}; a reasoned
 * decision is appended to the case hash-chain at each step; and the {@link DecisionEffect} for the
 * action runs <b>exactly once</b>, only on full completion. A human always decides.
 *
 * <p>The effect body is supplied by the consumer via the registry (never named here) — the
 * inversion. See {@link DecisionEffects}.
 */
public class ApprovalService {

    public static final String F_APPROVAL = "cmApproval";
    public static final String F_AUTH = "mmAuthority";
    public static final String F_CASE = "cmCase";
    public static final String F_COI = "mmCoi";
    private static final List<String> SCOPE = Collections.singletonList("DEFAULT");
    private static final int FETCH_ALL = 100000;
    private static final long SLA_DAYS = 2;        // default approval SLA (calendar days)
    private static final int MAX_ESCALATIONS = 2;  // escalate this many times, then time out
    private static final String CLASS_NAME = ApprovalService.class.getName();

    /** A resolved authority route for a (actionType, materiality). */
    static final class Route {
        final String kind;          // SINGLE | CHAIN | QUORUM
        final List<String> levels;  // CHAIN: ordered steps; SINGLE/QUORUM: [level]
        final int quorum;           // QUORUM: N; else 1
        final int slaDays;          // decision SLA for this band (config; default applied if blank)
        final int maxEscalations;   // escalations before timeout (config; default if blank)

        Route(String kind, List<String> levels, int quorum, int slaDays, int maxEscalations) {
            this.kind = kind;
            this.levels = levels;
            this.quorum = Math.max(quorum, 1);
            this.slaDays = slaDays;
            this.maxEscalations = maxEscalations;
        }

        String firstLevel() {
            return levels.isEmpty() ? "" : levels.get(0);
        }

        String describe() {
            if ("CHAIN".equals(kind)) {
                return String.join("→", levels) + " (chain)";
            }
            if ("QUORUM".equals(kind)) {
                return quorum + "×" + firstLevel() + " (quorum)";
            }
            return firstLevel();
        }
    }

    private final FormDataDao dao;
    private final CaseEventWriter events;
    private final StatusManager status;
    private final MmConfigService mm;
    private final Map<String, DecisionEffect> effects;

    public ApprovalService(FormDataDao dao, CaseEventWriter events, Map<String, DecisionEffect> effects) {
        this.dao = dao;
        this.events = events;
        this.effects = effects;
        this.mm = new MmConfigService(dao);
        this.status = new StatusManager(dao, mm, events);
    }

    // ---------------- REQUEST ----------------

    /** Raise an approval request (or auto-pass when no band matches the materiality). */
    public String request(String entity, String recordId, String actionType, double materiality,
                          String requester, String caseId, String actor, LocalDateTime now) {
        updateSchemas();
        FormRow live = liveRequest(recordId, actionType);
        if (live != null) {
            return "already pending (" + live.getId() + ")"; // idempotent
        }
        Route route = resolveRoute(actionType, materiality);
        if (route == null) {
            event(caseId, "APPROVAL_NOT_REQUIRED", actor,
                    "below approval band for " + actionType + " (materiality " + materiality + ")",
                    extra(entity, recordId, actionType, materiality, ""));
            return "AUTO (no approval required) -> " + runEffect(entity, recordId, actionType, actor, now);
        }
        FormRow ap = new FormRow();
        String apId = "AP-" + UUID.randomUUID().toString().substring(0, 8) + "-" + recordId;
        ap.setId(apId);
        ap.setProperty("entity", entity);
        ap.setProperty("recordId", recordId);
        ap.setProperty("actionType", actionType);
        ap.setProperty("materiality", String.valueOf(materiality));
        ap.setProperty("caseId", caseId);
        ap.setProperty("requestedBy", requester);
        ap.setProperty("routeKind", route.kind);
        ap.setProperty("requiredLevel", route.firstLevel());
        ap.setProperty("chain", "CHAIN".equals(route.kind) ? String.join(",", route.levels) : "");
        ap.setProperty("currentStep", "0");
        ap.setProperty("quorum", String.valueOf(route.quorum));
        ap.setProperty("approvalsCount", "0");
        ap.setProperty("voters", "");
        ap.setProperty("deadline", now.plusDays(route.slaDays).toString());
        ap.setProperty("escalations", "0");
        ap.setProperty("delegatedTo", "");
        ap.setProperty("delegatedBy", "");
        ap.setProperty("requiredAuthority", route.describe());
        ap.setProperty("status", "Pending"); // plain start (guarded moves run at decide)
        ap.setProperty("reason", "");
        save(ap);
        event(caseId, "APPROVAL_REQUESTED", actor,
                "approval requested: " + actionType + " -> " + route.describe(),
                extra(entity, recordId, actionType, materiality, route.firstLevel()));
        notify(caseId, roleGroupFor(route.firstLevel()), "APPROVAL_ASSIGNED",
                "approval needed: " + actionType + " -> " + route.firstLevel());
        return "PENDING (" + route.describe() + ", id=" + apId + ")";
    }

    /**
     * Resolve the authority route from {@code mmAuthority}: the band whose [amountMin, amountMax]
     * contains the materiality. {@code null} when no band matches (no approval required).
     */
    Route resolveRoute(String actionType, double materiality) {
        FormRow band = bandRow(actionType, materiality);
        if (band == null) {
            return null;
        }
        String level = p(band, "level");
        String bodyType = p(band, "bodyType");
        int q = (int) parseLong(p(band, "quorum"));
        String sd = p(band, "slaDays");
        int sla = sd.isEmpty() ? (int) SLA_DAYS : (int) parseLong(sd);
        String me = p(band, "maxEscalations");
        int maxEsc = me.isEmpty() ? MAX_ESCALATIONS : (int) parseLong(me);
        if ("CHAIN".equalsIgnoreCase(bodyType)) {
            List<String> steps = splitCsv(level);
            return steps.isEmpty() ? null : new Route("CHAIN", steps, steps.size(), sla, maxEsc);
        }
        if ("COLLEGIAL".equalsIgnoreCase(bodyType)) {
            return new Route("QUORUM", Collections.singletonList(level), Math.max(q, 1), sla, maxEsc);
        }
        return new Route("SINGLE", Collections.singletonList(level), 1, sla, maxEsc);
    }

    /** The mmAuthority band for (actionType, amount in [amountMin, amountMax]), or null. */
    private FormRow bandRow(String actionType, double materiality) {
        FormRowSet rows = dao.find(F_AUTH, F_AUTH,
                "WHERE e.customProperties.actionType = ?1", new Object[]{actionType},
                "dateCreated", Boolean.FALSE, 0, FETCH_ALL);
        if (rows == null) {
            return null;
        }
        // effective-dating: only bands valid today; among overlapping matches the most recently
        // effective (then highest version) wins. ISO yyyy-MM-dd sorts lexicographically.
        String today = LocalDateTime.now().toString().substring(0, 10);
        FormRow best = null;
        String bestFrom = "";
        long bestVer = -1;
        for (FormRow r : rows) {
            double min = num(p(r, "amountMin"));
            String maxStr = p(r, "amountMax");
            double max = maxStr.isEmpty() ? Double.MAX_VALUE : num(maxStr);
            if (materiality < min || materiality > max) {
                continue;
            }
            String from = p(r, "effectiveFrom");
            String to = p(r, "effectiveTo");
            if ((!from.isEmpty() && from.compareTo(today) > 0)
                    || (!to.isEmpty() && to.compareTo(today) < 0)) {
                continue; // not effective today
            }
            long ver = parseLong(p(r, "version"));
            if (best == null || from.compareTo(bestFrom) > 0
                    || (from.equals(bestFrom) && ver > bestVer)) {
                best = r;
                bestFrom = from;
                bestVer = ver;
            }
        }
        return best;
    }


    // ---------------- DECIDE ----------------

    /** An authority decides a Pending request. outcome ∈ approve | reject | return. */
    public String decide(String approvalId, String approver, String approverLevel, String outcome,
                         String reason, LocalDateTime now) {
        updateSchemas();
        FormRow ap = dao.load(F_APPROVAL, F_APPROVAL, approvalId);
        if (ap == null) {
            return "no approval " + approvalId;
        }
        if (!"Pending".equalsIgnoreCase(p(ap, "status"))) {
            return "already decided (" + p(ap, "status") + ")"; // gate-once
        }
        if (reason == null || reason.trim().isEmpty()) {
            return "reason required"; // a blank why hollows the record
        }
        String entity = p(ap, "entity");
        String recordId = p(ap, "recordId");
        String actionType = p(ap, "actionType");
        String caseId = p(ap, "caseId");
        double materiality = num(p(ap, "materiality"));
        String authority = p(ap, "requiredAuthority");
        // separation of duties — approver ≠ requester (four-eyes); blocking, request stays Pending
        if (approver != null && approver.equalsIgnoreCase(p(ap, "requestedBy"))) {
            event(caseId, "APPROVAL_SOD_BLOCKED", approver,
                    "separation of duties: approver == requester (" + approver + ")",
                    extra(entity, recordId, actionType, materiality, authority));
            return "SoD: approver == requester";
        }
        // conflict of interest — an approver barred from this subject by an mmCoi EXCLUDE_APPROVER
        // rule may not decide it at all (blocking, request stays Pending)
        if (coiBarred(approver, caseId)) {
            event(caseId, "APPROVAL_COI_BLOCKED", approver,
                    "conflict of interest: " + approver + " is barred from deciding this request",
                    extra(entity, recordId, actionType, materiality, authority));
            return "COI: approver barred";
        }
        // auto-derived COI — when an mmCoi EXCLUDE_DECISION_MAKER rule is in force for the case
        // type, one person may not decide more than one request for the SAME taxpayer (TIN).
        if (autoCoiBarred(approver, caseId, ap.getId())) {
            event(caseId, "APPROVAL_AUTOCOI_BLOCKED", approver,
                    "auto-COI: " + approver + " already decided another request for this taxpayer",
                    extra(entity, recordId, actionType, materiality, authority));
            return "auto-COI: already decided for this taxpayer";
        }
        // delegation binding — once a request is delegated, ONLY the named delegate may decide it.
        String delegatedTo = p(ap, "delegatedTo");
        if (!delegatedTo.isEmpty() && !delegatedTo.equalsIgnoreCase(approver)) {
            event(caseId, "APPROVAL_DELEGATE_BLOCKED", approver,
                    "delegated to " + delegatedTo + "; only the delegate may decide",
                    extra(entity, recordId, actionType, materiality, authority));
            return "delegated to " + delegatedTo + "; not the delegate";
        }

        // reject / return are terminal for the whole request (a reject anywhere stops the route)
        if (!"approve".equalsIgnoreCase(outcome)) {
            String target = "return".equalsIgnoreCase(outcome) ? "Returned" : "Rejected";
            finalize(ap, caseId, entity, recordId, actionType, materiality, authority,
                    target, approver, reason, now);
            return target.toUpperCase();
        }

        // approve: rank gate against the CURRENT required level (chain cursor / single / quorum)
        String requiredLevel = p(ap, "requiredLevel");
        if (DecisionService.rank(approverLevel) < DecisionService.rank(requiredLevel)) {
            event(caseId, "APPROVAL_RANK_BLOCKED", approver,
                    "rank too low: " + approverLevel + " below required " + requiredLevel,
                    extra(entity, recordId, actionType, materiality, authority));
            return "rank too low: " + approverLevel + " < " + requiredLevel;
        }
        // distinct voter — no one approves twice (quorum dedup + chain step distinctness); blocking
        List<String> voters = splitCsv(p(ap, "voters"));
        if (containsIgnoreCase(voters, approver)) {
            return "duplicate voter ignored (" + approver + ")";
        }
        voters.add(approver);
        ap.setProperty("voters", String.join(",", voters));
        event(caseId, "APPROVAL_DECISION", approver, "approve: " + reason,
                extra(entity, recordId, actionType, materiality, requiredLevel)
                        + ",\"outcome\":\"approve\",\"level\":\"" + CaseEventWriter.esc(approverLevel) + "\"");

        String kind = orDefault(p(ap, "routeKind"), "SINGLE");
        if ("CHAIN".equals(kind)) {
            List<String> chain = splitCsv(p(ap, "chain"));
            int step = (int) parseLong(p(ap, "currentStep")) + 1;
            if (step < chain.size()) {
                ap.setProperty("currentStep", String.valueOf(step));
                ap.setProperty("requiredLevel", chain.get(step));
                save(ap);
                return "chain advanced to step " + (step + 1) + "/" + chain.size()
                        + " (next: " + chain.get(step) + ")";
            }
            // last step done -> complete
        } else if ("QUORUM".equals(kind)) {
            int cnt = (int) parseLong(p(ap, "approvalsCount")) + 1;
            int quorum = Math.max((int) parseLong(p(ap, "quorum")), 1);
            ap.setProperty("approvalsCount", String.valueOf(cnt));
            if (cnt < quorum) {
                save(ap);
                return "quorum " + cnt + "/" + quorum + " (awaiting " + (quorum - cnt) + " more)";
            }
            // quorum reached -> complete
        }
        // SINGLE, or the completing step of CHAIN / QUORUM
        finalize(ap, caseId, entity, recordId, actionType, materiality, authority,
                "Approved", approver, reason, now);
        return "APPROVED -> " + runEffect(entity, recordId, actionType, approver, now);
    }

    /** Guarded + audited terminal transition (Approved / Rejected / Returned) + decision record. */
    private void finalize(FormRow ap, String caseId, String entity, String recordId, String actionType,
                          double materiality, String authority, String target, String approver,
                          String reason, LocalDateTime now) {
        ap.setProperty("decision", target);
        ap.setProperty("reason", reason);
        ap.setProperty("decidedBy", approver);
        ap.setProperty("decidedAt", now.toString());
        // guarded Pending -> target (sets status + a STATUS_CHANGED row on the chain)
        status.apply(F_APPROVAL, ap, "status", caseId, target, SCOPE, approver, reason);
        save(ap);
        event(caseId, "APPROVAL_DECISION", approver, target + ": " + reason,
                extra(entity, recordId, actionType, materiality, authority)
                        + ",\"outcome\":\"" + CaseEventWriter.esc(target) + "\"");
    }

    // ---------------- SWEEP (escalation / timeout) + DELEGATE ----------------

    /**
     * SWEEP: escalate or time out overdue Pending requests. A request past its deadline escalates
     * one rank up (the gate then needs a higher authority) up to {@link #MAX_ESCALATIONS}, after
     * which it is auto-<b>rejected</b> as a timeout (never auto-approved). {@code asOf} supports
     * time-travel for testing. Returns a one-line summary.
     */
    public String sweep(LocalDateTime asOf, String actor) {
        updateSchemas();
        FormRowSet rows = dao.find(F_APPROVAL, F_APPROVAL,
                "WHERE e.customProperties.status = ?1", new Object[]{"Pending"},
                null, Boolean.FALSE, 0, FETCH_ALL);
        int escalated = 0;
        int timedOut = 0;
        if (rows != null) {
            for (FormRow ap : rows) {
                String dl = p(ap, "deadline");
                if (dl.isEmpty() || !before(dl, asOf)) {
                    continue; // not overdue
                }
                String caseId = p(ap, "caseId");
                String entity = p(ap, "entity");
                String recordId = p(ap, "recordId");
                String actionType = p(ap, "actionType");
                double materiality = num(p(ap, "materiality"));
                Route route = resolveRoute(actionType, materiality);
                int maxEsc = route == null ? MAX_ESCALATIONS : route.maxEscalations;
                int slaDays = route == null ? (int) SLA_DAYS : route.slaDays;
                int esc = (int) parseLong(p(ap, "escalations"));
                if (esc < maxEsc) {
                    String from = p(ap, "requiredLevel");
                    String to = DecisionService.nextRank(from);
                    ap.setProperty("requiredLevel", to);
                    ap.setProperty("escalations", String.valueOf(esc + 1));
                    ap.setProperty("deadline", asOf.plusDays(slaDays).toString());
                    ap.setProperty("requiredAuthority", to + " (escalated x" + (esc + 1) + ")");
                    save(ap);
                    event(caseId, "APPROVAL_ESCALATED", actor,
                            "overdue: escalated " + from + " -> " + to + " (#" + (esc + 1) + ")",
                            extra(entity, recordId, actionType, materiality, to));
                    notify(caseId, roleGroupFor(to), "APPROVAL_ESCALATED",
                            "overdue approval escalated to " + to);
                    escalated++;
                } else {
                    String reason = "SLA timeout after " + esc + " escalations";
                    event(caseId, "APPROVAL_TIMEOUT", actor, reason,
                            extra(entity, recordId, actionType, materiality, p(ap, "requiredLevel")));
                    notify(caseId, p(ap, "requestedBy"), "APPROVAL_TIMEOUT",
                            "approval timed out (auto-rejected after " + esc + " escalations)");
                    finalize(ap, caseId, entity, recordId, actionType, materiality,
                            p(ap, "requiredAuthority"), "Rejected", actor, reason, asOf);
                    timedOut++;
                }
            }
        }
        return "swept asOf " + asOf + ": escalated=" + escalated + ", timedOut=" + timedOut;
    }

    /**
     * DELEGATE: reassign a Pending request to another approver (a "please handle this" hand-off).
     * The delegate still decides under the normal rank gate + SoD — delegation routes the work, it
     * does not confer authority. Delegation also <b>binds</b>: once delegated, ONLY the delegate may
     * decide the request (enforced in {@link #decide}); the delegate is notified.
     */
    public String delegate(String approvalId, String fromApprover, String delegateTo, String reason,
                           LocalDateTime now) {
        updateSchemas();
        FormRow ap = dao.load(F_APPROVAL, F_APPROVAL, approvalId);
        if (ap == null) {
            return "no approval " + approvalId;
        }
        if (!"Pending".equalsIgnoreCase(p(ap, "status"))) {
            return "already decided (" + p(ap, "status") + ")";
        }
        if (delegateTo == null || delegateTo.trim().isEmpty()) {
            return "delegate target required";
        }
        if (reason == null || reason.trim().isEmpty()) {
            return "reason required";
        }
        ap.setProperty("delegatedTo", delegateTo);
        ap.setProperty("delegatedBy", fromApprover);
        save(ap);
        event(p(ap, "caseId"), "APPROVAL_DELEGATED", fromApprover,
                "delegated to " + delegateTo + ": " + reason,
                extra(p(ap, "entity"), p(ap, "recordId"), p(ap, "actionType"),
                        num(p(ap, "materiality")), p(ap, "requiredLevel")));
        notify(p(ap, "caseId"), delegateTo, "APPROVAL_DELEGATED",
                "approval delegated to you: " + reason);
        return "DELEGATED to " + delegateTo;
    }

    // ---------------- helpers ----------------

    private static boolean before(String iso, LocalDateTime asOf) {
        try {
            return LocalDateTime.parse(iso).isBefore(asOf);
        } catch (Exception e) {
            return false;
        }
    }

    /** mmCoi rules for a case type (read directly; the platform reader keeps COI out of core config). */
    private FormRowSet coiRules(String caseType) {
        if (caseType == null || caseType.isEmpty()) {
            return null;
        }
        return dao.find(F_COI, F_COI, "WHERE e.customProperties.caseType = ?1",
                new Object[]{caseType}, "dateCreated", Boolean.FALSE, null, null);
    }

    /**
     * COI: true when an {@code mmCoi} EXCLUDE_APPROVER rule for the request's case type bars this
     * approver from this subject. The rule {@code expression} is a comma list of {@code approver|tin}
     * tokens; {@code *} is a wildcard on either side ({@code *|TIN} bars everyone for one taxpayer;
     * {@code user|*} bars one user across the case type).
     */
    private boolean coiBarred(String approver, String caseId) {
        if (approver == null || approver.isEmpty() || caseId == null || caseId.isEmpty()) {
            return false;
        }
        FormRow c = dao.load(F_CASE, F_CASE, caseId);
        if (c == null) {
            return false;
        }
        FormRowSet rules = coiRules(p(c, "caseType"));
        if (rules == null) {
            return false;
        }
        String tin = p(c, "tin");
        for (FormRow r : rules) {
            if (!"EXCLUDE_APPROVER".equalsIgnoreCase(p(r, "ruleType"))) {
                continue;
            }
            for (String token : p(r, "expression").split("[,;]")) {
                String[] ab = token.trim().split("\\|", 2);
                String who = ab[0].trim();
                String which = ab.length > 1 ? ab[1].trim() : "*";
                if (("*".equals(who) || who.equalsIgnoreCase(approver))
                        && ("*".equals(which) || which.equalsIgnoreCase(tin))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Auto-derived COI: when an {@code mmCoi} {@code EXCLUDE_DECISION_MAKER} rule is in force for the
     * case type, the same person may not decide more than one approval request for the same taxpayer.
     * True when {@code approver} is already a recorded voter on some OTHER request whose case has the
     * same TIN. Derived from the decision record itself — no separate declaration.
     */
    boolean autoCoiBarred(String approver, String caseId, String currentApId) {
        if (approver == null || approver.isEmpty() || caseId == null || caseId.isEmpty()) {
            return false;
        }
        FormRow c = dao.load(F_CASE, F_CASE, caseId);
        if (c == null) {
            return false;
        }
        String tin = p(c, "tin");
        if (tin.isEmpty()) {
            return false;
        }
        boolean enabled = false;
        FormRowSet rules = coiRules(p(c, "caseType"));
        if (rules != null) {
            for (FormRow r : rules) {
                if ("EXCLUDE_DECISION_MAKER".equalsIgnoreCase(p(r, "ruleType"))) {
                    enabled = true;
                    break;
                }
            }
        }
        if (!enabled) {
            return false;
        }
        FormRowSet voted = dao.find(F_APPROVAL, F_APPROVAL,
                "WHERE e.customProperties.voters LIKE ?1", new Object[]{"%" + approver + "%"},
                null, Boolean.FALSE, 0, FETCH_ALL);
        if (voted != null) {
            for (FormRow o : voted) {
                if (o.getId().equals(currentApId)) {
                    continue; // the current request itself is not "another"
                }
                if (!containsIgnoreCase(splitCsv(p(o, "voters")), approver)) {
                    continue;
                }
                FormRow oc = dao.load(F_CASE, F_CASE, p(o, "caseId"));
                if (oc != null && tin.equalsIgnoreCase(p(oc, "tin"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String runEffect(String entity, String recordId, String actionType, String actor,
                             LocalDateTime now) {
        DecisionEffect eff = effects == null ? null : effects.get(actionType);
        if (eff == null) {
            return "no DecisionEffect for " + actionType;
        }
        try {
            return eff.run(entity, recordId, actor, now);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "DecisionEffect failed for " + actionType + " " + recordId);
            return "effect error: " + e.getMessage();
        }
    }

    private FormRow liveRequest(String recordId, String actionType) {
        FormRowSet rows = dao.find(F_APPROVAL, F_APPROVAL,
                "WHERE e.customProperties.recordId = ?1 AND e.customProperties.actionType = ?2",
                new Object[]{recordId, actionType}, null, Boolean.FALSE, 0, FETCH_ALL);
        if (rows != null) {
            for (FormRow r : rows) {
                if ("Pending".equalsIgnoreCase(p(r, "status"))) {
                    return r;
                }
            }
        }
        return null;
    }

    private String extra(String entity, String recordId, String actionType, double materiality,
                         String authority) {
        return "\"entity\":\"" + CaseEventWriter.esc(entity) + "\""
                + ",\"recordId\":\"" + CaseEventWriter.esc(recordId) + "\""
                + ",\"actionType\":\"" + CaseEventWriter.esc(actionType) + "\""
                + ",\"materiality\":\"" + materiality + "\""
                + ",\"authority\":\"" + CaseEventWriter.esc(authority) + "\"";
    }

    private void event(String caseId, String type, String actor, String reason, String extra) {
        events.append(caseId, type, actor, "", "", reason, extra);
    }

    /**
     * Queue a lifecycle notification: emit a {@code NOTIF_PENDING} event carrying a recipient, which
     * a consumer dispatcher turns into an alert. Used on assignment, escalation, timeout and
     * delegation so the gate is not silent about who must act next.
     */
    private void notify(String caseId, String recipient, String alertType, String summary) {
        if (caseId == null || caseId.isEmpty() || recipient == null || recipient.trim().isEmpty()) {
            return;
        }
        event(caseId, "NOTIF_PENDING", "system", summary,
                "\"recipient\":\"" + CaseEventWriter.esc(recipient) + "\",\"alertType\":\""
                        + CaseEventWriter.esc(alertType) + "\"");
    }

    /** The role-group that holds a rank level (reverse of the mmRoleLevel map); the level as fallback. */
    private String roleGroupFor(String level) {
        try {
            Map<String, String> m = new AuthorityResolver(dao, null).roleLevelMap();
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (level != null && level.equalsIgnoreCase(e.getValue())) {
                    return e.getKey();
                }
            }
        } catch (Exception ignore) {
            // fall through to the rank token
        }
        return level;
    }

    private void save(FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(F_APPROVAL, F_APPROVAL, set);
    }

    private void updateSchemas() {
        dao.updateSchema(F_APPROVAL, F_APPROVAL, new FormRowSet());
    }

    private static String p(FormRow r, String field) {
        if (r == null) {
            return "";
        }
        String v = r.getProperty(field);
        return v == null ? "" : v;
    }

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static List<String> splitCsv(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null) {
            return out;
        }
        for (String t : s.split(",")) {
            String x = t.trim();
            if (!x.isEmpty()) {
                out.add(x);
            }
        }
        return out;
    }

    private static boolean containsIgnoreCase(List<String> list, String v) {
        if (v == null) {
            return false;
        }
        for (String s : list) {
            if (s.equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    private static double num(String s) {
        try {
            return (s == null || s.trim().isEmpty()) ? 0 : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return (s == null || s.trim().isEmpty()) ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
