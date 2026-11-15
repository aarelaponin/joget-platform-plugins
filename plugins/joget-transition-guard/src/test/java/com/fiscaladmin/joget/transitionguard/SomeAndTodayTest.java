package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A guard term over another record's rows that must be there, and a date compared with today (1.12.0;
 * METHOD-2026-11-05-97, R55; MT-DM's wave 12, the escalation held, requests 2 and 3).
 *
 * <p>{@code some(<entity> where <condition>)} holds where at least one row of that record meets the condition; it is
 * read by the one pattern and the one count that read {@code none(...)}. A comparison {@code <attribute> before today}
 * holds where the attribute holds a day before the move's own day, the day of its one reading of the clock in the
 * clock's zone; {@code not before today} is its negation; an empty date is not before today. The count is made by the
 * database; here it is made by {@link Rows}, which reads the guard's own condition (each form {@code condition} writes,
 * and nothing else: an unknown form fails the test) over rows of the test's own, as the database would compare the
 * stored strings. The guard's whole run is driven through {@link TransitionGuard#run}; FormDataDao, MmConfigService and
 * the event writer are mocked, the state machine is real.
 */
public class SomeAndTodayTest {

    /** The escalation's term of MT-DM's hand-off (a_v83_escalation_held, request 2 with 3). */
    static final String SOME_SERVED = "some(notice where debt_case = this and kind = final_demand"
            + " and date_of_service is not null and response_deadline before today)";
    static final String COUNTED = "WHERE e.customProperties.debt_case = ?1 AND e.customProperties.kind = ?2 AND "
            + "(e.customProperties.date_of_service IS NOT NULL AND e.customProperties.date_of_service <> '') AND "
            + "(e.customProperties.response_deadline IS NOT NULL AND e.customProperties.response_deadline <> '' "
            + "AND e.customProperties.response_deadline < ?3)";
    /** 10:00 UTC on 13 November 2026: in UTC and in Malta (UTC+1) the day is the 13th. */
    static final Instant MORNING = Instant.parse("2026-11-13T10:00:00Z");

    private FormDataDao dao;
    private MmConfigService cfg;
    private StatusManager sm;
    private Rows notices;
    private FormRow act;
    private FormRow dcase;

    static final RowStamp.Who ANNA = new RowStamp.Who() {
        public String username() { return "anna"; }
        public String fullName() { return "Anna Grech"; }
    };

    private static FormRow row(String id, String... kv) {
        FormRow r = new FormRow();
        r.setId(id);
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private static FormRowSet set(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        s.addAll(Arrays.asList(rs));
        return s;
    }

    /** The escalation by hand of a debt case, by its trigger form, guarded by {@code guard}. */
    private static Map<String, String> trigger(String guard) {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "debt_case");
        p.put("tableName", "mtcadm_debt_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "escalate_by_hand>in_progress>>" + guard + ">");
        p.put("records", "notice>recNotice>mtcadm_notice");
        p.put("thisKey", "case_number");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actEscalate");
        p.put("actTable", "mtcadm_debt_case_act");
        p.put("checkRoles", "false");
        p.put("copyFields", "");
        return p;
    }

    private TransitionGuard guardAt(Instant instant, String zone) {
        return new TransitionGuard(Clock.fixed(instant, ZoneId.of(zone)), ANNA);
    }

    /** The case's notices, the rows the term counts. */
    private void theCaseHas(FormRow... rows) {
        notices = new Rows(rows);
        when(dao.count(eq("recNotice"), eq("mtcadm_notice"), anyString(), any(Object[].class)))
                .thenAnswer(inv -> notices.count(inv.getArgument(2), inv.getArgument(3)));
    }

    private void theCase(String caseNumber) {
        act = row("act-1", "lc_action", "escalate_by_hand", "record_key", caseNumber);
        dcase = row("case-" + caseNumber, "case_number", caseNumber, "state", "open");
        when(dao.load("actEscalate", "mtcadm_debt_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("mtcadm_debt_case"), eq("WHERE e.customProperties.case_number = ?1"),
                any(Object[].class), isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(dcase));
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        sm = new StatusManager(dao, cfg, mock(CaseEventWriter.class));
        when(cfg.entityScopeHasRules("debt_case", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("debt_case", "DEFAULT")).thenReturn(set(row("m1", "fromStatus", "open",
                "toStatus", "in_progress", "transitionCode", "escalate_by_hand", "roles", "officer")));
        theCase("DC-2026-011472");
        theCaseHas();
    }

    private String escalate(String guard, Instant at, String zone) {
        return guardAt(at, zone).run(trigger(guard), dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
    }

    private static FormRow notice(String ref, String caseNumber, String kind, String served, String deadline) {
        FormRow r = row(ref, "notice_reference", ref, "debt_case", caseNumber, "kind", kind);
        if (served != null) r.setProperty("date_of_service", served);
        if (deadline != null) r.setProperty("response_deadline", deadline);
        return r;
    }

    // ---------------------------------------------------------------------------------------- some(...)

    @Test
    public void someHoldsWhereARowIsFoundAndTheMoveIsMade() {
        theCaseHas(notice("FD-2026-011476", "DC-2026-011472", "final_demand", "2026-09-20", "2026-10-05"));
        assertEquals("OK: in_progress", escalate(SOME_SERVED, MORNING, "UTC"));
        assertEquals("in_progress", dcase.getProperty("state"));
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).count(eq("recNotice"), eq("mtcadm_notice"), eq(COUNTED), params.capture());
        assertArrayEquals(new Object[]{"DC-2026-011472", "final_demand", "2026-11-13"}, params.getValue());
    }

    @Test
    public void someFailsWhereNoRowIsFoundAndTheRecordStaysAsItWas() {
        theCaseHas(notice("RM-2026-013261", "DC-2026-011472", "reminder", "2026-09-01", "2026-09-15"));
        String result = escalate(SOME_SERVED, MORNING, "UTC");
        assertEquals("REJECTED: guard failed: " + SOME_SERVED + " (0 row(s) of notice)", result);
        assertEquals("open", dcase.getProperty("state"));
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void someThatCannotBeCountedRefusesTheMove() {
        when(dao.count(anyString(), anyString(), anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("no such column"));
        String result = escalate(SOME_SERVED, MORNING, "UTC");
        assertTrue(result, result.startsWith("REJECTED: guard '" + SOME_SERVED + "' could not be counted"));
        assertEquals("open", dcase.getProperty("state"));
    }

    @Test
    public void someOverARecordTheGuardIsNotToldOfRefusesTheMove() {
        Map<String, String> p = trigger(SOME_SERVED);
        p.put("records", "assignment>frmAssignment>mtcadm_assignment");
        String result = guardAt(MORNING, "UTC").run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        assertEquals("REJECTED: guard '" + SOME_SERVED + "': the guard is not told where 'notice' is kept (records)",
                result);
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
    }

    @Test
    public void noneAndSomeAreOneCountReadTheOtherWayRound() {
        TransitionGuard g = guardAt(MORNING, "UTC");
        Map<String, String> p = trigger(SOME_SERVED);
        theCaseHas(notice("FD-1", "DC-2026-011472", "final_demand", "2026-09-20", "2026-10-05"));
        String none = "none(notice where debt_case = this and kind = final_demand)";
        String some = "some(notice where debt_case = this and kind = final_demand)";
        assertEquals("guard failed: " + none + " (1 row(s) of notice)", g.evalOther(none, dcase, dcase.getId(), p, dao, "2026-11-13"));
        assertNull(g.evalOther(some, dcase, dcase.getId(), p, dao, "2026-11-13"));
        theCaseHas();
        assertNull(g.evalOther(none, dcase, dcase.getId(), p, dao, "2026-11-13"));
        assertEquals("guard failed: " + some + " (0 row(s) of notice)", g.evalOther(some, dcase, dcase.getId(), p, dao, "2026-11-13"));
        // evalNone is the same reading of a none term, as before 1.12.0
        assertNull(g.evalNone(none, dcase, dcase.getId(), p, dao));
        assertTrue(TransitionGuard.isOther(some) && TransitionGuard.isOther(none) && !TransitionGuard.isOther("amount eq 0"));
    }

    // ---------------------------------------------------------------------------------------- the condition, as before

    @Test
    public void everyOldComparisonIsWrittenExactlyAsBefore() {
        String[][] cases = {
            {"dcase = this", "WHERE e.customProperties.dcase = ?1", "[SELF]"},
            {"kind = visit", "WHERE e.customProperties.kind = ?1", "[visit]"},
            {"kind != visit", "WHERE (e.customProperties.kind <> ?1 OR e.customProperties.kind IS NULL)", "[visit]"},
            {"kind in (visit, contact)", "WHERE e.customProperties.kind IN (?1, ?2)", "[visit, contact]"},
            {"kind not in (visit, contact)",
             "WHERE (e.customProperties.kind NOT IN (?1, ?2) OR e.customProperties.kind IS NULL)", "[visit, contact]"},
            {"ended_on is null", "WHERE (e.customProperties.ended_on IS NULL OR e.customProperties.ended_on = '')", "[]"},
            {"ended_on is not null",
             "WHERE (e.customProperties.ended_on IS NOT NULL AND e.customProperties.ended_on <> '')", "[]"},
        };
        for (String[] c : cases) {
            for (String today : new String[]{null, "2026-11-13"}) {
                List<Object> params = new ArrayList<Object>();
                assertEquals(c[0], c[1], TransitionGuard.condition(c[0], "SELF", today, params));
                assertEquals(c[0], c[2], params.toString());
            }
            List<Object> params = new ArrayList<Object>();
            assertEquals(c[0], c[1], TransitionGuard.condition(c[0], "SELF", params));
        }
        assertNull(TransitionGuard.condition("kind like visit", "SELF", "2026-11-13", new ArrayList<Object>()));
    }

    @Test
    public void aComparisonWithTodayIsWrittenWithTheMovesDay() {
        List<Object> params = new ArrayList<Object>();
        assertEquals("WHERE e.customProperties.dcase = ?1 AND (e.customProperties.due IS NOT NULL AND "
                + "e.customProperties.due <> '' AND e.customProperties.due < ?2)",
                TransitionGuard.condition("dcase = this and due before today", "DC-1", "2026-11-13", params));
        assertEquals(Arrays.<Object>asList("DC-1", "2026-11-13"), params);
        params = new ArrayList<Object>();
        assertEquals("WHERE (e.customProperties.due IS NULL OR e.customProperties.due = '' OR "
                + "e.customProperties.due >= ?1)",
                TransitionGuard.condition("due not before today", "DC-1", "2026-11-13", params));
        assertEquals(Arrays.<Object>asList("2026-11-13"), params);
        // without the day, a comparison with today cannot be read, and the term refuses
        assertNull(TransitionGuard.condition("due before today", "DC-1", params));
        assertNull(TransitionGuard.condition("due before today", "DC-1", " ", new ArrayList<Object>()));
        assertNull(TransitionGuard.condition("due before tomorrow", "DC-1", "2026-11-13", new ArrayList<Object>()));
    }

    @Test
    public void aTermWithTodayAndNoDayRefusesTheMove() {
        TransitionGuard g = guardAt(MORNING, "UTC");
        assertEquals("guard unparseable: '" + SOME_SERVED + "'",
                g.evalGuard(SOME_SERVED, dcase, dcase.getId(), trigger(SOME_SERVED), dao));
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
    }

    // ---------------------------------------------------------------------------------------- the day's edges

    /** Which of a case's notices, each with one deadline, meet {@code <deadline> before today} on the move's day. */
    private List<String> before(String term, Instant at, String zone) {
        theCaseHas(notice("Y", "DC-2026-011472", "final_demand", "2026-09-20", "2026-11-12"),
                   notice("T", "DC-2026-011472", "final_demand", "2026-09-20", "2026-11-13"),
                   notice("M", "DC-2026-011472", "final_demand", "2026-09-20", "2026-11-14"),
                   notice("E", "DC-2026-011472", "final_demand", "2026-09-20", ""),
                   notice("N", "DC-2026-011472", "final_demand", "2026-09-20", null));
        List<String> hit = new ArrayList<String>();
        for (String ref : new String[]{"Y", "T", "M", "E", "N"}) {
            String t = "some(notice where notice_reference = " + ref + " and " + term + ")";
            if (guardAt(at, zone).evalGuard(t, dcase, dcase.getId(), trigger(t), dao,
                    guardAt(at, zone).stampFor(trigger(t)).at.toLocalDate().toString()) == null) hit.add(ref);
        }
        return hit;
    }

    @Test
    public void yesterdayIsBeforeTodayAndTodayTomorrowAndAnEmptyDateAreNot() {
        assertEquals(Arrays.asList("Y"), before("response_deadline before today", MORNING, "UTC"));
        assertEquals(Arrays.asList("T", "M", "E", "N"), before("response_deadline not before today", MORNING, "UTC"));
    }

    @Test
    public void theDayIsTheClocksDayInTheClocksZone() {
        // 23:30 UTC on 12 November is 00:30 on the 13th in Malta: there, the 12th is before today
        Instant late = Instant.parse("2026-11-12T23:30:00Z");
        assertEquals(Arrays.asList(), before("response_deadline before today", late, "UTC"));
        assertEquals(Arrays.asList("Y"), before("response_deadline before today", late, "Europe/Malta"));
        // the platform's guard reads Clock.systemDefaultZone(): the JVM's default time zone
        assertEquals(ZoneId.systemDefault(), Clock.systemDefaultZone().getZone());
        LocalDate d0 = LocalDate.now(ZoneId.systemDefault());
        LocalDate day = new TransitionGuard().stampNow().at.toLocalDate();
        LocalDate d1 = LocalDate.now(ZoneId.systemDefault());
        assertTrue(day + " between " + d0 + " and " + d1, !day.isBefore(d0) && !day.isAfter(d1));
    }

    @Test
    public void aMomentHandedInGivesTheDay() {
        Map<String, String> p = trigger(SOME_SERVED);
        p.put(TransitionGuard.MOVE_MOMENT, "2026-10-04T12:00:00Z");
        theCaseHas(notice("FD-2026-011476", "DC-2026-011472", "final_demand", "2026-09-20", "2026-10-05"));
        // on 4 October the deadline of the 5th has not run out
        assertTrue(guardAt(MORNING, "UTC").run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna")
                .startsWith("REJECTED: guard failed: " + SOME_SERVED));
    }

    // ---------------------------------------------------------------------------------------- MT-DM's rows (item 5)

    @Test
    public void theEscalationTermJudgesMtDmsCasesAsItsRuleDoes() {
        String[][] cases = {
            // case, the notices it holds, the outcome
            {"DC-2026-013260", "REJECTED"},   // two reminders, no final demand
            {"SB-RUNNING", "REJECTED"},       // a final demand served, its period running
            {"DC-2026-011472", "OK"},         // a final demand served, its period run out on 5 October
            {"SB-NODATE", "REJECTED"},        // a final demand run out, with no date of service
        };
        for (String[] c : cases) {
            setUp();
            theCase(c[0]);
            switch (c[0]) {
                case "DC-2026-013260":
                    theCaseHas(notice("RM-2026-013261", c[0], "reminder", "2026-08-01", "2026-08-15"),
                               notice("RM-2026-013262", c[0], "reminder", "2026-09-01", "2026-09-15"));
                    break;
                case "SB-RUNNING":
                    theCaseHas(notice("FD-RUN", c[0], "final_demand", "2026-11-01", "2026-11-30"));
                    break;
                case "DC-2026-011472":
                    theCaseHas(notice("FD-2026-011476", c[0], "final_demand", "2026-09-20", "2026-10-05"));
                    break;
                default:
                    theCaseHas(notice("FD-NODATE", c[0], "final_demand", "", "2026-10-05"));
            }
            String result = escalate(SOME_SERVED, MORNING, "Europe/Malta");
            assertTrue(c[0] + ": " + result, result.startsWith(c[1]));
            assertEquals(c[0], "OK".equals(c[1]) ? "in_progress" : "open", dcase.getProperty("state"));
        }
    }

    // ---------------------------------------------------------------------------------------- a related set_attr

    @Test
    public void aRelatedLinesConditionReadsTheMovesDay() {
        Map<String, String> p = trigger("");
        p.put("related", "escalate_by_hand>notice>debt_case = this and response_deadline before today>overdue='yes'");
        FormRow fd = notice("FD-2026-011476", "DC-2026-011472", "final_demand", "2026-09-20", "2026-10-05");
        when(dao.find(eq("recNotice"), eq("mtcadm_notice"), anyString(), any(Object[].class),
                isNull(), isNull(), isNull(), isNull())).thenReturn(set(fd));
        assertEquals("OK: in_progress", guardAt(MORNING, "UTC").run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna"));
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).find(eq("recNotice"), eq("mtcadm_notice"), eq("WHERE e.customProperties.debt_case = ?1 AND "
                + "(e.customProperties.response_deadline IS NOT NULL AND e.customProperties.response_deadline <> '' "
                + "AND e.customProperties.response_deadline < ?2)"), params.capture(), isNull(), isNull(), isNull(), isNull());
        assertArrayEquals(new Object[]{"DC-2026-011472", "2026-11-13"}, params.getValue());
        assertEquals("yes", fd.getProperty("overdue"));
    }

    // ---------------------------------------------------------------------------------------- MoveRefusal's words

    @Test
    public void aFailedSomeIsWordedAsAFailedNoneIs() {
        FormRow r = row("c", "case_number", "DC-2026-013260");
        assertEquals("Escalate this case now is refused: it is made only where there is a notice where debt case is "
                + "this record and kind = final demand and date of service is not null and response deadline before "
                + "today, and none is found.",
                MoveRefusal.guardWords("Escalate this case now", SOME_SERVED,
                        "guard failed: " + SOME_SERVED + " (0 row(s) of notice)", r));
        // a none term is worded exactly as before
        String none = "none(action where dcase = this and kind = visit)";
        assertEquals("Close is refused: it is made only while there is no action where dcase is this record and "
                + "kind = visit, and 1 is found.",
                MoveRefusal.guardWords("Close", none, "guard failed: " + none + " (1 row(s) of action)", r));
        assertEquals("Close is refused: amount must be 0, and it is empty.",
                MoveRefusal.guardWords("Close", "amount eq 0", "guard failed", r));
    }

    @Test
    public void moveRefusalJudgesSomeBeforeTheSaveAndFillsItsWordsFromTheRecord() {
        Map<String, String> p = trigger(SOME_SERVED);
        p.put("fromState", "open");
        p.put("refusals", SOME_SERVED + "|Case {case_number} has no final demand whose period has run out.");
        theCase("DC-2026-013260");
        theCaseHas(notice("RM-2026-013261", "DC-2026-013260", "reminder", "2026-08-01", "2026-08-15"));
        String words = new MoveRefusal(guardAt(MORNING, "UTC")).refuse(p, dao, "anna", "DC-2026-013260", "");
        assertEquals("Case DC-2026-013260 has no final demand whose period has run out.", words);
        // no model words: the guard's own
        p.remove("refusals");
        p.put("moveName", "Escalate this case now");
        assertTrue(new MoveRefusal(guardAt(MORNING, "UTC")).refuse(p, dao, "anna", "DC-2026-013260", "")
                .endsWith(", and none is found."));
        theCase("DC-2026-011472");
        theCaseHas(notice("FD-2026-011476", "DC-2026-011472", "final_demand", "2026-09-20", "2026-10-05"));
        assertNull(new MoveRefusal(guardAt(MORNING, "UTC")).refuse(p, dao, "anna", "DC-2026-011472", ""));
    }

    @Test
    public void bothClassesReportTheBundlesNewVersion() {
        assertEquals("1.12.0", new TransitionGuard().getVersion());
        assertEquals("1.12.0", new MoveRefusal().getVersion());
    }

    /**
     * Rows of a table, and the count of those that meet a condition the guard writes ({@link TransitionGuard#condition}),
     * compared as the database compares the stored strings: each form the guard writes is read here, and any other
     * fails the test.
     */
    static final class Rows {
        private final List<FormRow> rows;
        private static final String C = "e\\.customProperties\\.([A-Za-z0-9_]+)";
        private static final Pattern EQ = Pattern.compile("^" + C + " = \\?(\\d+)$");
        private static final Pattern NE = Pattern.compile("^\\(" + C + " <> \\?(\\d+) OR " + C + " IS NULL\\)$");
        private static final Pattern IN = Pattern.compile("^" + C + " IN \\(([?0-9, ]+)\\)$");
        private static final Pattern NIN = Pattern.compile("^\\(" + C + " NOT IN \\(([?0-9, ]+)\\) OR " + C + " IS NULL\\)$");
        private static final Pattern NUL = Pattern.compile("^\\(" + C + " IS NULL OR " + C + " = ''\\)$");
        private static final Pattern NNUL = Pattern.compile("^\\(" + C + " IS NOT NULL AND " + C + " <> ''\\)$");
        private static final Pattern BEF = Pattern.compile(
                "^\\(" + C + " IS NOT NULL AND " + C + " <> '' AND " + C + " < \\?(\\d+)\\)$");
        private static final Pattern NBEF = Pattern.compile(
                "^\\(" + C + " IS NULL OR " + C + " = '' OR " + C + " >= \\?(\\d+)\\)$");

        Rows(FormRow... rows) { this.rows = Arrays.asList(rows); }

        long count(String cond, Object[] params) {
            assertTrue(cond, cond.startsWith("WHERE "));
            List<String> parts = ands(cond.substring(6));
            long n = 0;
            for (FormRow r : rows) {
                boolean all = true;
                for (String part : parts) all &= meets(r, part.trim(), params);
                if (all) n++;
            }
            return n;
        }

        private static boolean meets(FormRow r, String part, Object[] p) {
            Matcher m;
            if ((m = EQ.matcher(part)).matches()) return p[i(m, 2)].equals(r.getProperty(m.group(1)));
            if ((m = NE.matcher(part)).matches()) {
                String v = r.getProperty(m.group(1));
                return v == null || !v.equals(p[i(m, 2)]);
            }
            if ((m = IN.matcher(part)).matches()) return values(m.group(2), p).contains(r.getProperty(m.group(1)));
            if ((m = NIN.matcher(part)).matches()) {
                String v = r.getProperty(m.group(1));
                return v == null || !values(m.group(2), p).contains(v);
            }
            if ((m = NUL.matcher(part)).matches()) return empty(r.getProperty(m.group(1)));
            if ((m = NNUL.matcher(part)).matches()) return !empty(r.getProperty(m.group(1)));
            if ((m = BEF.matcher(part)).matches()) {
                String v = r.getProperty(m.group(1));
                return !empty(v) && v.compareTo((String) p[i(m, 4)]) < 0;
            }
            if ((m = NBEF.matcher(part)).matches()) {
                String v = r.getProperty(m.group(1));
                return empty(v) || v.compareTo((String) p[i(m, 4)]) >= 0;
            }
            fail("a condition the test cannot read: " + part);
            return false;
        }

        /** The parts of an HQL condition joined by {@code AND}, split outside parentheses only. */
        static List<String> ands(String s) {
            List<String> out = new ArrayList<String>();
            int depth = 0, start = 0;
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && s.startsWith(" AND ", k)) {
                    out.add(s.substring(start, k));
                    start = k + 5;
                    k += 4;
                }
            }
            out.add(s.substring(start));
            return out;
        }

        private static int i(Matcher m, int g) { return Integer.parseInt(m.group(g)) - 1; }

        private static boolean empty(String v) { return v == null || v.isEmpty(); }

        private static List<Object> values(String list, Object[] p) {
            List<Object> out = new ArrayList<Object>();
            for (String x : list.split(",")) out.add(p[Integer.parseInt(x.trim().substring(1)) - 1]);
            return out;
        }
    }
}
