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
 * The approval row and its events take one reading (METHOD-2026-11-05-51, R34 version 2, gap G1;
 * the finding of version 1's review): the request row (and the decision row) a move saves is
 * stamped from the same moment its events are handed - the {@code now} the move is called with,
 * {@code asOf} for the sweep - and not from a second reading of the service's clock at the save.
 * Version 1's tests fixed the service clock at the move's moment, so the two readings were the
 * same by construction and could not differ; here the service's clock <em>moves</em>, one second
 * on every reading and an hour after the move's moment, so a row stamped from the clock cannot
 * be stamped as its events are. The event writer is the real one over a mocked FormDataDao, the
 * state machine is real, and the platform's user is passed in: a person, or nobody.
 */
public class OneReadingTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 20, 25, 25, 400_000_000);
    static final Date AT = Date.from(NOW.atZone(MALTA).toInstant());
    static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 8, 21, 0, 0);
    static final Date AS_OF_AT = Date.from(AS_OF.atZone(MALTA).toInstant());
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);

    /** A clock that moves: an hour after the move's moment at the first reading, a second more at each next. */
    static final class MovingClock extends Clock {
        private int reads;
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            return NOW.atZone(MALTA).toInstant().plusSeconds(3600 + reads++);
        }
        int reads() { return reads; }
    }

    private FormDataDao dao;
    private MovingClock clock;

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

    private RowStamps stamps(final String user, final String name) {
        return new RowStamps(clock, new RowStamps.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        });
    }

    private CaseEventWriter writer(final String user, final String name) {
        return new CaseEventWriter(dao, ApprovalService.F_EVENT, WRITER_CLOCK,
                new com.fiscaladmin.joget.eventchain.RowStamp.Who() {
                    @Override public String username() { return user; }
                    @Override public String fullName() { return name; }
                });
    }

    private ApprovalService service(String user, String name) {
        return new ApprovalService(dao, writer(user, name), new HashMap<String, DecisionEffect>(),
                stamps(user, name));
    }

    @Before
    public void setUp() {
        clock = new MovingClock();
        dao = mock(FormDataDao.class);
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any()))
                .thenReturn(1L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(),
                anyString(), any(), any(), any()))
                .thenReturn(set(transition("Pending", "Approved"),
                        transition("Pending", "Rejected"),
                        transition("Pending", "Returned")));
        when(dao.find(eq("cmApproval"), eq("cmApproval"),
                contains("recordId"), any(), any(), any(), anyInt(), anyInt()))
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
        for (FormRowSet s : cap.getAllValues()) {
            assertEquals(1, s.size());
            out.add(s.get(0));
        }
        return out;
    }

    private static FormRow ofType(List<FormRow> events, String type) {
        for (FormRow e : events) {
            if (type.equals(e.getProperty("eventType"))) return e;
        }
        fail("no " + type + " event among " + events.size());
        return null;
    }

    /** The row and every event of the move: the six columns from the one moment, by the one user. */
    private static void assertOneReading(FormRow row, List<FormRow> events, Date at, String user, String name) {
        assertFalse("some event was written", events.isEmpty());
        assertEquals("row dateModified", at, row.getDateModified());
        assertEquals("row dateCreated", at, row.getDateCreated());
        assertEquals("row modifiedBy", user, row.getModifiedBy());
        assertEquals("row createdBy", user, row.getCreatedBy());
        assertEquals("row modifiedByName", name, row.getModifiedByName());
        assertEquals("row createdByName", name, row.getCreatedByName());
        for (FormRow e : events) {
            String w = e.getProperty("eventType");
            assertEquals(w + " dateModified", row.getDateModified(), e.getDateModified());
            assertEquals(w + " dateCreated", row.getDateCreated(), e.getDateCreated());
            assertEquals(w + " modifiedBy", row.getModifiedBy(), e.getModifiedBy());
            assertEquals(w + " modifiedByName", row.getModifiedByName(), e.getModifiedByName());
        }
    }

    // ------------------------------------------------------------------------------ the request

    @Test
    public void theRequestRowAndItsEventsAreOneReading() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(set(row("b1", "level", "MANAGER", "bodyType", "",
                "quorum", "", "amountMin", "0", "amountMax", "")));
        String r = service("anna.grech", "Anna Grech").request("dmWriteOff", "W1", "WRITE_OFF", 5000,
                "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("PENDING"));
        List<FormRow> rows = saved("cmApproval");
        assertEquals(1, rows.size());
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        assertTrue("the request event and the notice", ev.size() >= 2);
        ofType(ev, "APPROVAL_REQUESTED");
        assertOneReading(rows.get(0), ev, AT, "anna.grech", "Anna Grech");
        assertEquals("the service's clock was not read for a stamp", 0, clock.reads());
    }

    // -------------------------------------------------------------------------------- the decide

    @Test
    public void theApprovalRowAndItsEventsAreOneReading() {
        FormRow ap = pendingAp();
        String r = service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        assertTrue("the approve event and the terminal move's events", ev.size() >= 3);
        ofType(ev, "STATUS_CHANGED");
        ofType(ev, "APPROVAL_DECISION");
        assertOneReading(ap, ev, AT, "anna.grech", "Anna Grech");
        assertEquals("the service's clock was not read for a stamp", 0, clock.reads());
    }

    @Test
    public void theRejectedRowAndItsEventsAreOneReading() {
        FormRow ap = pendingAp();
        assertEquals("REJECTED", service("anna.grech", "Anna Grech")
                .decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW));
        assertOneReading(ap, saved(ApprovalService.F_EVENT), AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aQuorumVotesRowAndEventAreOneReading() {
        FormRow ap = pendingAp();
        ap.setProperty("routeKind", "QUORUM");
        ap.setProperty("quorum", "2");          // one vote of two: the row is saved and the request stays Pending
        String r = service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        assertTrue(r, r.startsWith("quorum 1/2"));
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        ofType(ev, "APPROVAL_DECISION");
        assertOneReading(ap, ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aChainStepsRowAndEventAreOneReading() {
        FormRow ap = pendingAp();
        ap.setProperty("routeKind", "CHAIN");
        ap.setProperty("chain", "MANAGER,DIRECTOR");
        ap.setProperty("currentStep", "0");     // step 1 of 2 done: the chain advances, the row is saved, the request stays Pending
        String r = service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        assertTrue(r, r.startsWith("chain advanced"));
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        ofType(ev, "APPROVAL_DECISION");
        assertOneReading(ap, ev, AT, "anna.grech", "Anna Grech");
    }

    // ----------------------------------------------------------------------- sweep and delegate

    @Test
    public void theEscalatedRowAndItsEventsAreOneReadingAtAsOf() {
        FormRow ap = pendingAp();
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        String r = service("anna.grech", "Anna Grech").sweep(AS_OF, "system");
        assertTrue(r, r.contains("escalated=1"));
        assertOneReading(ap, saved(ApprovalService.F_EVENT), AS_OF_AT, "anna.grech", "Anna Grech");
        assertEquals("the service's clock was not read for a stamp", 0, clock.reads());
    }

    @Test
    public void theTimedOutRowAndItsEventsAreOneReadingAtAsOf() {
        FormRow ap = pendingAp();
        ap.setProperty("escalations", "99");   // past the escalations allowed: auto-rejected as a timeout
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        String r = service("anna.grech", "Anna Grech").sweep(AS_OF, "system");
        assertTrue(r, r.contains("timedOut=1"));
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        ofType(ev, "APPROVAL_TIMEOUT");
        assertOneReading(ap, ev, AS_OF_AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theDelegatedRowAndItsEventsAreOneReading() {
        FormRow ap = pendingAp();
        assertEquals("DELEGATED to carla", service("anna.grech", "Anna Grech")
                .delegate("AP-1", "bob", "carla", "away", NOW));
        assertOneReading(ap, saved(ApprovalService.F_EVENT), AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theRowAndTheEventsOfAMoveWithNobodySignedInAreOneReadingByTheSameValue() {
        FormRow ap = pendingAp();
        service("roleAnonymous", null).decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW);
        assertOneReading(ap, saved(ApprovalService.F_EVENT), AT, "roleAnonymous", null);
    }

    // ---------------------------------------------------------------------- the decision service

    @Test
    public void theDecisionRowAndItsTwoEventsAreOneReading() {
        FormRow d = row("D1", "actionType", "WRITE_OFF", "amount", "100", "approverLevel", "MANAGER",
                "reasons", "sound grounds", "caseId", "C1");
        when(dao.load("cmDecision", "cmDecision", "D1")).thenReturn(d);
        String r = new DecisionService(dao, writer("anna.grech", "Anna Grech"), stamps("anna.grech", "Anna Grech"))
                .decide("D1", "bob", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        List<FormRow> ev = saved(ApprovalService.F_EVENT);
        assertEquals(2, ev.size());
        assertOneReading(d, ev, AT, "anna.grech", "Anna Grech");
        assertEquals("the service's clock was not read for a stamp", 0, clock.reads());
    }

    // ------------------------------------------------------------- a row that belongs to no move

    @Test
    public void aRowSavedWithNoMomentIsStampedFromTheClockReadOnce() {
        RowStamps s = stamps("anna.grech", "Anna Grech");
        FormRow plain = row("X1");
        s.save(dao, "cmApprovalCheck", "cmApprovalCheck", plain);
        assertEquals("the clock was read once", 1, clock.reads());
        assertEquals(Date.from(NOW.atZone(MALTA).toInstant().plusSeconds(3600)), plain.getDateModified());
        FormRow nullMoment = row("X2");
        s.save(dao, "cmApprovalCheck", "cmApprovalCheck", nullMoment, null);
        assertEquals("a null moment reads the clock once more", 2, clock.reads());
        FormRow withMoment = row("X3");
        s.save(dao, "cmApprovalCheck", "cmApprovalCheck", withMoment, NOW);
        assertEquals("a moment reads no clock", 2, clock.reads());
        assertEquals(AT, withMoment.getDateModified());
    }
}
