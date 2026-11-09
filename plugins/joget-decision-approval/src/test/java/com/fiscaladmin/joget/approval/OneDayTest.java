package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
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
 * The approvals read one day (METHOD-2026-11-05-67, R40; the two clock readings the review of R36 left in this
 * bundle, named in MT-DM's request of 9 November 2026).
 *
 * <p>{@code ApprovalService.bandRow} looked the authority matrix up at {@code LocalDateTime.now()}, whatever day
 * the move was made for: a sweep given a past {@code asOf} resolved its bands at the day the sweep was run. It
 * reads the day it is handed - the request's {@code now}, the sweep's {@code asOf}, the route engine's one
 * reading. The route and authority-matrix engines read the clock once and save every row they write on that
 * reading, as the gate, delegate and sweep engines do since R36 ({@link EngineOneReadingTest}).
 *
 * <p>The matrix used throughout has two bands for one action, one in force until 30 June 2026 (level MANAGER,
 * three escalations, ten days) and one in force from 1 July 2026 (level DIRECTOR, no escalation, two days), so the
 * band chosen shows which day it was looked up at. The clocks are set to another day than the move's, and
 * {@link Ticking} moves one second on every read and counts the reads. Cases marked REGRESSION hold on HEAD as
 * well, and are here to keep the count of readings at one; the others fail on HEAD (9e27757).
 */
public class OneDayTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** A day of the band that ended on 30 June 2026. */
    static final LocalDateTime PAST_DAY = LocalDateTime.of(2026, 3, 15, 9, 0, 0);
    /** A day of the band that began on 1 July 2026. */
    static final LocalDateTime LATER_DAY = LocalDateTime.of(2026, 9, 1, 9, 0, 0);
    static final Instant ANOTHER_CLOCK = Instant.parse("2027-02-01T10:00:00Z");

    /** A clock that moves one second on every read, counted; its first reading is {@code start}. */
    static final class Ticking extends Clock {
        final Instant start;
        int reads;
        Ticking(Instant start) { this.start = start; }
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return start.plusSeconds(reads++); }
    }

    private static Instant at(LocalDateTime t) { return t.atZone(MALTA).toInstant(); }

    private FormDataDao dao;

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

    private static FormRow band(String level, String sla, String maxEsc, String from, String to, String version) {
        return row(null, "actionType", "WRITE_OFF", "amountMin", "0", "amountMax", "", "level", level,
                "bodyType", "SINGLE", "quorum", "", "slaDays", sla, "maxEscalations", maxEsc,
                "effectiveFrom", from, "effectiveTo", to, "version", version);
    }

    private RowStamps nobody(Clock clock) {
        return new RowStamps(clock, new RowStamps.Who() {
            @Override public String username() { return "roleAnonymous"; }
            @Override public String fullName() { return null; }
        });
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any())).thenReturn(1L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(set(transition("Pending", "Approved"), transition("Pending", "Rejected"),
                        transition("Pending", "Returned")));
        when(dao.find(eq("cmApproval"), eq("cmApproval"), contains("recordId"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FormRowSet());
        when(dao.load(eq("cmCase"), eq("cmCase"), anyString())).thenReturn(null);
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(), any(), anyInt(), anyInt()))
                .thenReturn(set(band("MANAGER", "10", "3", "2026-01-01", "2026-06-30", "1"),
                        band("DIRECTOR", "2", "0", "2026-07-01", "", "2")));
    }

    private static FormRow transition(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    private FormRow pendingAp() {
        FormRow ap = row("AP-1", "status", "Pending", "entity", "dmWriteOff", "recordId", "W1",
                "actionType", "WRITE_OFF", "caseId", "C1", "requestedBy", "alice", "routeKind", "SINGLE",
                "requiredLevel", "SUPERVISOR", "voters", "", "quorum", "1", "approvalsCount", "0",
                "materiality", "500.0", "deadline", "2026-03-01T10:00", "escalations", "0");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        return ap;
    }

    private String sweep(String asOf, Clock clock) {
        FormRow run = row("SW1", "asOf", asOf);
        when(dao.load("cmApprovalSweep", "cmApprovalSweep", "SW1")).thenReturn(run);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "SW1");
        new ApprovalSweepEngine(nobody(clock)).run(p, dao, "system");
        return run.getProperty("result");
    }

    private List<FormRow> saved(String form) {
        return saved(form, form);
    }

    private List<FormRow> saved(String form, String table) {
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), cap.capture());
        List<FormRow> out = new ArrayList<FormRow>();
        for (FormRowSet s : cap.getAllValues()) out.addAll(s);
        return out;
    }

    // ------------------------------------------------------------- bandRow reads the day it is handed

    @Test
    public void aSweepGivenAPastAsOfResolvesItsBandsAtThatDayUnderAClockOfAnotherDay() {
        FormRow ap = pendingAp();
        String result = sweep("2026-03-15T09:00:00", new Ticking(ANOTHER_CLOCK));
        assertTrue(result, result.contains("escalated=1, timedOut=0"));
        assertEquals("the band in force at the asOf: ten days", "2026-03-25T09:00", ap.getProperty("deadline"));
        assertEquals("1", ap.getProperty("escalations"));
        assertEquals("Pending", ap.getProperty("status"));
    }

    /** REGRESSION: an asOf in the later band, a clock in the earlier one - the clock is not read for the day. */
    @Test
    public void aSweepGivenALaterAsOfResolvesItsBandsAtThatDayUnderAClockOfAnotherDay() {
        FormRow ap = pendingAp();
        String result = sweep("2026-09-01T09:00:00", new Ticking(at(PAST_DAY)));
        assertTrue(result, result.contains("escalated=0, timedOut=1"));
        assertEquals("Rejected", ap.getProperty("status"));
    }

    @Test
    public void aRequestMadeForAPastDayIsRoutedByTheBandOfThatDay() {
        Ticking clock = new Ticking(ANOTHER_CLOCK);
        ApprovalService svc = new ApprovalService(dao, new CaseEventWriter(dao, ApprovalService.F_EVENT),
                new HashMap<String, DecisionEffect>(), nobody(clock));
        String said = svc.request("dmWriteOff", "W1", "WRITE_OFF", 500.0, "alice", "C1", "alice", PAST_DAY);
        assertTrue(said, said.startsWith("PENDING"));
        FormRow ap = saved("cmApproval").get(0);
        assertEquals("MANAGER", ap.getProperty("requiredLevel"));
        assertEquals("the band's ten days", "2026-03-25T09:00", ap.getProperty("deadline"));
        assertEquals("the move's day was handed to the service; the clock was not read for it", 0, clock.reads);
    }

    // ------------------------------------------------------------- the route engine, one reading

    private static final String ENTITY = "write_off";
    private static final String TABLE = "mtcadm_write_off";

    private FormRow record() {
        FormRow r = row("WO1", "standing", "requested", "lc_action", "route_for_approval", "total_amount", "500.00");
        when(dao.load(ENTITY, TABLE, "WO1")).thenReturn(r);
        return r;
    }

    private Map<String, Object> routeProps() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("entity", ENTITY);
        p.put("tableName", TABLE);
        p.put("statusField", "standing");
        p.put("actionField", "lc_action");
        p.put("routes", "route_for_approval>awaiting_approval>total_amount>approval_required_from>WRITE_OFF");
        p.put("recordId", "WO1");
        return p;
    }

    private static ApprovalRouteEngine.Guard applies(final FormRow record, final FormRow act) {
        return (className, properties) -> {
            record.setProperty("standing", "awaiting_approval");
            (act == null ? record : act).setProperty("transition_result", "OK: awaiting_approval");
            record.setProperty("lc_action", "");
            return true;
        };
    }

    @Test
    public void theRouteEngineLooksTheBandUpAtTheDayOfItsOneReadingOfTheClock() {
        FormRow rec = record();
        Ticking clock = new Ticking(at(PAST_DAY));
        String said = new ApprovalRouteEngine(nobody(clock)).run(routeProps(), dao, applies(rec, null));
        assertEquals(said, "MANAGER", rec.getProperty("approval_required_from"));
        assertEquals(1, clock.reads);
    }

    /** REGRESSION: one form, one save: the record is stamped at the one reading, which is the only one. */
    @Test
    public void theRouteEngineSavesTheRecordOfOneFormOnOneReading() {
        FormRow rec = record();
        Ticking clock = new Ticking(at(PAST_DAY));
        new ApprovalRouteEngine(nobody(clock)).run(routeProps(), dao, applies(rec, null));
        assertEquals(1, saved(ENTITY, TABLE).size());
        assertEquals(Date.from(at(PAST_DAY)), rec.getDateModified());
        assertEquals(1, clock.reads);
    }

    @Test
    public void theRouteEngineSavesTheRecordAndTheActOfATriggerFormOnOneReading() {
        FormRow rec = record();
        FormRow act = row("ACT1", "lc_action", "route_for_approval", "record_key", "WO-1");
        when(dao.load("actWriteOffRoute", "mtcadm_write_off_act", "ACT1")).thenReturn(act);
        when(dao.find(eq("frmWriteOff"), eq(TABLE), contains("write_off_reference"), any(), any(), any(), any(), any()))
                .thenReturn(set(rec));
        Map<String, Object> p = routeProps();
        p.put("recordId", "ACT1");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "write_off_reference");
        p.put("targetForm", "frmWriteOff");
        p.put("actForm", "actWriteOffRoute");
        p.put("actTable", "mtcadm_write_off_act");
        Ticking clock = new Ticking(at(LATER_DAY));      // the band is the same at HEAD (the system day) and after
        new ApprovalRouteEngine(nobody(clock)).run(p, dao, applies(rec, act));
        assertEquals("DIRECTOR", rec.getProperty("approval_required_from"));
        assertEquals(1, saved("frmWriteOff", TABLE).size());
        assertEquals(1, saved("actWriteOffRoute", "mtcadm_write_off_act").size());
        assertEquals("the record and the act, one stamp", rec.getDateModified(), act.getDateModified());
        assertEquals(Date.from(at(LATER_DAY)), act.getDateModified());
        assertEquals(1, clock.reads);
    }

    /** REGRESSION: the guard not installed - the one save of the act is on the one reading. */
    @Test
    public void aGuardNotInstalledIsSavedOnTheOneReading() {
        FormRow rec = record();
        Ticking clock = new Ticking(at(PAST_DAY));
        String said = new ApprovalRouteEngine(nobody(clock)).run(routeProps(), dao, (c, p) -> false);
        assertTrue(said, said.startsWith("ERROR"));
        assertEquals(Date.from(at(PAST_DAY)), rec.getDateModified());
        assertEquals(1, clock.reads);
    }

    // ------------------------------------------------------------- the authority matrix engine, one reading

    private FormRow check() {
        FormRow c = row("CK1", "actionType", "WRITE_OFF");
        when(dao.load("cmAuthorityCheck", "cmAuthorityCheck", "CK1")).thenReturn(c);
        return c;
    }

    /**
     * Two bands that overlap only on days up to and including 15 March 2026 (the first ends then, the second
     * began on 1 January). The clock starts at 23:59:59.5 on 15 March: a second reading of it, the validation's
     * day apart from the save's stamp, would be on 16 March, where the bands do not overlap.
     */
    @Test
    public void theMatrixEngineValidatesAtTheDayOfItsOneReadingAndStampsItsCheckWithTheSame() {
        FormRow ck = check();
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(), any(), anyInt(), anyInt()))
                .thenReturn(set(
                        row(null, "amountMin", "0", "amountMax", "1000", "level", "MANAGER", "effectiveFrom", "",
                                "effectiveTo", "2026-03-15", "version", "1"),
                        row(null, "amountMin", "500", "amountMax", "", "level", "DIRECTOR", "effectiveFrom",
                                "2026-01-01", "effectiveTo", "", "version", "2")));
        Instant start = LocalDateTime.of(2026, 3, 15, 23, 59, 59, 500_000_000).atZone(MALTA).toInstant();
        Ticking clock = new Ticking(start);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "CK1");
        new AuthorityMatrixEngine(nobody(clock)).run(p, dao);
        assertEquals(ck.getProperty("result"), "false", ck.getProperty("valid"));
        assertEquals("1", ck.getProperty("issueCount"));
        assertTrue(ck.getProperty("issues"), ck.getProperty("issues").startsWith("overlap"));
        assertEquals(Date.from(start), ck.getDateModified());
        assertEquals(1, saved("cmAuthorityCheck").size());
        assertEquals("the validation's day and the save's stamp are one reading", 1, clock.reads);
    }

    /** REGRESSION: the check is stamped once, from the one reading, whatever the bands. */
    @Test
    public void theMatrixEngineSavesTheCheckOnOneReading() {
        FormRow ck = check();
        Ticking clock = new Ticking(at(PAST_DAY));
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "CK1");
        new AuthorityMatrixEngine(nobody(clock)).run(p, dao);
        assertEquals(Date.from(at(PAST_DAY)), ck.getDateModified());
        assertEquals(1, clock.reads);
    }
}
