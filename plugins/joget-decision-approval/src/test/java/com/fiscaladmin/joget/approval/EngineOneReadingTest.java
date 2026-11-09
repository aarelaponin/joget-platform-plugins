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
 * The gate, delegate and sweep engines take one reading (METHOD-2026-11-05-57, R36; the finding G2 of the
 * review of R34 version 2): the engine's own result row ({@code cmApprovalDecision}, {@code cmApprovalDelegate},
 * {@code cmApprovalSweep}) is stamped from the same moment as the approval row and the events of the move it
 * ran - the {@code now} the engine read once and called the service with, {@code asOf} for the sweep - and not
 * from a second reading of the clock at its own save. Here the engine's clock <em>moves</em>, one second on
 * every read, so a result row stamped from a second reading differs from the approval row and the events by a
 * second, and the number of reads is counted: one for a move that starts from the clock, none for a sweep
 * that was given its {@code asOf}. The platform's user is passed in as nobody signed in.
 */
public class EngineOneReadingTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final Instant START = Instant.parse("2026-11-09T06:15:30.250Z");
    static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 8, 21, 0, 0);
    static final Date AS_OF_AT = Date.from(AS_OF.atZone(MALTA).toInstant());

    /** A clock that moves one second on every read; its first reading is {@link #START}. */
    static final class Ticking extends Clock {
        int reads;
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return START.plusSeconds(reads++); }
    }

    private FormDataDao dao;
    private Ticking clock;

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

    private static FormRow transition(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    private RowStamps nobody() {
        return new RowStamps(clock, new RowStamps.Who() {
            @Override public String username() { return "roleAnonymous"; }
            @Override public String fullName() { return null; }
        });
    }

    @Before
    public void setUp() {
        clock = new Ticking();
        dao = mock(FormDataDao.class);
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any())).thenReturn(1L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(set(transition("Pending", "Approved"), transition("Pending", "Rejected"),
                        transition("Pending", "Returned")));
        when(dao.find(eq("cmApproval"), eq("cmApproval"), contains("recordId"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FormRowSet());
        when(dao.load(eq("cmCase"), eq("cmCase"), anyString())).thenReturn(null);
    }

    private FormRow pendingAp() {
        FormRow ap = row("AP-1", "status", "Pending", "entity", "dmWriteOff", "recordId", "W1",
                "actionType", "WRITE_OFF", "caseId", "C1", "requestedBy", "alice", "routeKind", "SINGLE",
                "requiredLevel", "MANAGER", "voters", "", "quorum", "1", "approvalsCount", "0",
                "deadline", "2026-11-01T10:00", "escalations", "0");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        return ap;
    }

    private List<FormRow> saved(String form) {
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(form), cap.capture());
        List<FormRow> out = new ArrayList<FormRow>();
        for (FormRowSet s : cap.getAllValues()) out.addAll(s);
        return out;
    }

    /**
     * The engine's result row, the approval row and every event of the move: the dates from the one moment
     * {@code at}, the engine's result row made by the same user as the approval row, the engine's one save of
     * its result row, and the number of readings of the clock the whole move took.
     */
    private void assertOneMoment(String what, FormRow result, String resultForm, FormRow ap, Date at, int reads) {
        assertEquals(what + ": the engine saved its result row once", 1, saved(resultForm).size());
        assertSame(what + ": the row saved is the result row", result, saved(resultForm).get(0));
        assertEquals(what + ": result row dateModified", at, result.getDateModified());
        assertEquals(what + ": result row dateCreated", at, result.getDateCreated());
        assertEquals(what + ": result row modifiedBy", "roleAnonymous", result.getModifiedBy());
        assertEquals(what + ": approval row dateModified", at, ap.getDateModified());
        assertEquals(what + ": approval row modifiedBy", "roleAnonymous", ap.getModifiedBy());
        assertEquals(what + ": result row and approval row, same modified moment",
                ap.getDateModified(), result.getDateModified());
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        assertFalse(what + ": some event was written", ev.isEmpty());
        for (FormRow e : ev) {
            String w = what + ": " + e.getProperty("eventType");
            assertEquals(w + " dateModified", result.getDateModified(), e.getDateModified());
            assertEquals(w + " dateCreated", result.getDateCreated(), e.getDateCreated());
        }
        assertEquals(what + ": the clock was read " + reads + " time(s) for the whole move", reads, clock.reads);
    }

    @Test
    public void theGateEnginesResultRowTheApprovalRowAndTheEventsAreOneReading() {
        FormRow ap = pendingAp();
        FormRow dec = row("DEC1", "approvalId", "AP-1", "outcome", "reject", "reason", "no", "approverLevel", "DIRECTOR");
        when(dao.load("cmApprovalDecision", "cmApprovalDecision", "DEC1")).thenReturn(dec);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DEC1");
        new ApprovalGateEngine(nobody()).run(p, dao, "bob");
        assertEquals("REJECTED", dec.getProperty("result"));
        assertOneMoment("gate", dec, "cmApprovalDecision", ap, Date.from(START), 1);
    }

    @Test
    public void theDelegateEnginesResultRowTheApprovalRowAndTheEventsAreOneReading() {
        FormRow ap = pendingAp();
        FormRow del = row("DL1", "approvalId", "AP-1", "delegateTo", "carla", "reason", "away");
        when(dao.load("cmApprovalDelegate", "cmApprovalDelegate", "DL1")).thenReturn(del);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DL1");
        new ApprovalDelegateEngine(nobody()).run(p, dao, "bob");
        assertEquals("DELEGATED to carla", del.getProperty("result"));
        assertOneMoment("delegate", del, "cmApprovalDelegate", ap, Date.from(START), 1);
    }

    private FormRow sweepSetup(FormRow ap, String asOf) {
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        FormRow run = row("SW1", "asOf", asOf);
        when(dao.load("cmApprovalSweep", "cmApprovalSweep", "SW1")).thenReturn(run);
        return run;
    }

    private void runSweep() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "SW1");
        new ApprovalSweepEngine(nobody()).run(p, dao, "system");
    }

    @Test
    public void theSweepEnginesResultRowTheApprovalRowAndTheEventsAreOneReadingWithABlankAsOf() {
        FormRow ap = pendingAp();
        FormRow run = sweepSetup(ap, "");
        runSweep();
        assertTrue(run.getProperty("result"), run.getProperty("result").contains("escalated=1"));
        assertOneMoment("sweep, blank asOf", run, "cmApprovalSweep", ap, Date.from(START), 1);
    }

    @Test
    public void theSweepEnginesResultRowTheApprovalRowAndTheEventsAreOneReadingWithAnUnparseableAsOf() {
        FormRow ap = pendingAp();
        FormRow run = sweepSetup(ap, "not a date");
        runSweep();
        assertTrue(run.getProperty("result"), run.getProperty("result").contains("escalated=1"));
        assertOneMoment("sweep, unparseable asOf", run, "cmApprovalSweep", ap, Date.from(START), 1);
    }

    @Test
    public void theSweepEnginesResultRowTheApprovalRowAndTheEventsAreOneMomentAtAGivenAsOfAndNoClockIsRead() {
        FormRow ap = pendingAp();
        FormRow run = sweepSetup(ap, "2026-11-08T21:00:00");
        runSweep();
        assertTrue(run.getProperty("result"), run.getProperty("result").contains("escalated=1"));
        assertOneMoment("sweep, given asOf", run, "cmApprovalSweep", ap, AS_OF_AT, 0);
    }
}
