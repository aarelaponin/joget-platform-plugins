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
import com.fiscaladmin.joget.eventchain.ChainVerifyService;
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
 * The events the decision and approval service appends are stamped as the platform stamps a row,
 * from the move's own moment (METHOD-2026-11-05-51, R34; MT-DM's answer of 8 November 2026 to
 * item 46's acceptance). The move's moment is the {@code now} it is called with ({@code asOf} for
 * the sweep), 20:25:25 in Europe/Malta; the service's rows are stamped from a clock fixed at that
 * same moment; and the event writer's own clock is fixed years later, so an event carrying the
 * move's moment was handed it and did not read its own. The event writer is the real one over a
 * mocked FormDataDao, the state machine is real, and the platform's user is passed in: a person,
 * or nobody.
 */
public class EventStampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 20, 25, 25, 400_000_000);
    static final Instant NOW_INSTANT = NOW.atZone(MALTA).toInstant();   // 2026-11-08T19:25:25.400Z
    static final Date AT = Date.from(NOW_INSTANT);
    static final Clock SERVICE_CLOCK = Clock.fixed(NOW_INSTANT, MALTA);
    /** The sweep's time-travel moment: 21:00:00 the same evening. */
    static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 8, 21, 0, 0);
    static final Date AS_OF_AT = Date.from(AS_OF.atZone(MALTA).toInstant());
    /** The event writer's own clock, which must not be what an event is stamped from here. */
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);

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

    private static FormRow transition(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    private static RowStamps stamps(final String user, final String name) {
        return new RowStamps(SERVICE_CLOCK, new RowStamps.Who() {
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

    /** Every event row saved to the event carrier, in order. */
    private List<FormRow> events() {
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(ApprovalService.F_EVENT), eq(ApprovalService.F_EVENT),
                cap.capture());
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

    /** Each event: the six columns from the one moment and the platform's user. */
    private static void assertAllStamped(List<FormRow> events, Date at, String user, String name) {
        assertFalse("some event was written", events.isEmpty());
        for (FormRow e : events) {
            String w = e.getProperty("eventType");
            assertEquals(w + " dateCreated", at, e.getDateCreated());
            assertEquals(w + " dateModified", at, e.getDateModified());
            assertEquals(w + " createdBy", user, e.getCreatedBy());
            assertEquals(w + " modifiedBy", user, e.getModifiedBy());
            assertEquals(w + " createdByName", name, e.getCreatedByName());
            assertEquals(w + " modifiedByName", name, e.getModifiedByName());
        }
    }

    // ------------------------------------------------------------------------------ the request

    @Test
    public void theEventsOfARequestAreStampedFromTheMovesMomentByThePerson() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(set(row("b1", "level", "MANAGER", "bodyType", "",
                "quorum", "", "amountMin", "0", "amountMax", "")));
        String r = service("anna.grech", "Anna Grech").request("dmWriteOff", "W1", "WRITE_OFF", 5000,
                "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("PENDING"));
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_REQUESTED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T20:25:25.400", ofType(ev, "APPROVAL_REQUESTED").getProperty("eventTime"));
    }

    @Test
    public void theEventOfARequestBelowTheApprovalBandIsStampedFromTheMovesMoment() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());   // no band: AUTO
        String r = service("anna.grech", "Anna Grech").request("dmWriteOff", "W1", "WRITE_OFF", 5,
                "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("AUTO"));
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_NOT_REQUIRED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    // -------------------------------------------------------------------------------- the decide

    @Test
    public void theEventsOfAnApprovalAreStampedFromTheMovesMoment() {
        pendingAp();
        String r = service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        List<FormRow> ev = events();
        assertTrue("the approve event and the terminal move's events", ev.size() >= 3);
        ofType(ev, "STATUS_CHANGED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aRankBlockedDecisionsEventIsStampedFromTheMovesMoment() {
        pendingAp();
        service("anna.grech", "Anna Grech").decide("AP-1", "bob", "OFFICER", "approve", "ok", NOW);   // below MANAGER
        assertAllStamped(events(), AT, "anna.grech", "Anna Grech");
        ofType(events(), "APPROVAL_RANK_BLOCKED");
    }

    @Test
    public void aDelegateBlockedDecisionsEventIsStampedFromTheMovesMoment() {
        FormRow ap = pendingAp();
        ap.setProperty("delegatedTo", "carla");
        service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_DELEGATE_BLOCKED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theTimeoutEventsOfTheSweepAreStampedFromItsAsOfMoment() {
        FormRow ap = pendingAp();
        ap.setProperty("escalations", "99");   // past the escalations allowed: auto-rejected as a timeout
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        String r = service("anna.grech", "Anna Grech").sweep(AS_OF, "system");
        assertTrue(r, r.contains("timedOut=1"));
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_TIMEOUT");
        ofType(ev, "STATUS_CHANGED");
        assertAllStamped(ev, AS_OF_AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theStatusEventAndTheDecisionEventOfARejectionAreStampedFromTheMovesMoment() {
        pendingAp();
        assertEquals("REJECTED", service("anna.grech", "Anna Grech")
                .decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW));
        List<FormRow> ev = events();
        ofType(ev, "STATUS_CHANGED");     // appended by the state machine inside the move
        ofType(ev, "APPROVAL_DECISION");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aBlockedDecisionsEventIsStampedFromTheMovesMomentToo() {
        pendingAp();
        service("anna.grech", "Anna Grech").decide("AP-1", "alice", "DIRECTOR", "approve", "ok", NOW);
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_SOD_BLOCKED");   // approver == requester
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theConflictOfInterestBlockIsStampedFromTheMovesMoment() {
        pendingAp();
        when(dao.load(eq("cmCase"), eq("cmCase"), eq("C1")))
                .thenReturn(row("C1", "caseType", "AUDIT", "tin", "TIN-1"));
        when(dao.find(eq("mmCoi"), eq("mmCoi"), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(set(row("R1", "ruleType", "EXCLUDE_APPROVER", "expression", "bob|*")));
        service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_COI_BLOCKED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theAutoDerivedConflictBlockIsStampedFromTheMovesMoment() {
        pendingAp();
        when(dao.load(eq("cmCase"), eq("cmCase"), eq("C1")))
                .thenReturn(row("C1", "caseType", "AUDIT", "tin", "TIN-1"));
        when(dao.load(eq("cmCase"), eq("cmCase"), eq("C2")))
                .thenReturn(row("C2", "caseType", "AUDIT", "tin", "TIN-1"));
        when(dao.find(eq("mmCoi"), eq("mmCoi"), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(set(row("R1", "ruleType", "EXCLUDE_DECISION_MAKER", "expression", "")));
        when(dao.find(eq("cmApproval"), eq("cmApproval"),
                eq("WHERE e.customProperties.voters LIKE ?1"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(set(row("AP-2", "voters", "bob", "caseId", "C2")));
        service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "approve", "ok", NOW);
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_AUTOCOI_BLOCKED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theEventsOfAMoveWithNobodySignedInTakeThePlatformsOwnValue() {
        pendingAp();
        service("roleAnonymous", null).decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW);
        assertAllStamped(events(), AT, "roleAnonymous", null);
    }

    // ----------------------------------------------------------------------- sweep and delegate

    @Test
    public void theEventsOfTheSweepAreStampedFromItsAsOfMoment() {
        FormRow ap = pendingAp();
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        String r = service("anna.grech", "Anna Grech").sweep(AS_OF, "system");
        assertTrue(r, r.contains("escalated=1"));
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_ESCALATED");
        assertAllStamped(ev, AS_OF_AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theEventsOfADelegationAreStampedFromTheMovesMoment() {
        pendingAp();
        assertEquals("DELEGATED to carla", service("anna.grech", "Anna Grech")
                .delegate("AP-1", "bob", "carla", "away", NOW));
        List<FormRow> ev = events();
        ofType(ev, "APPROVAL_DELEGATED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    // ---------------------------------------------------------------------- the decision service

    @Test
    public void bothEventsOfADecisionRecordAreStampedFromTheMovesMoment() {
        FormRow d = row("D1", "actionType", "WRITE_OFF", "amount", "100", "approverLevel", "MANAGER",
                "reasons", "sound grounds", "caseId", "C1");
        when(dao.load("cmDecision", "cmDecision", "D1")).thenReturn(d);
        String r = new DecisionService(dao, writer("anna.grech", "Anna Grech"), stamps("anna.grech", "Anna Grech"))
                .decide("D1", "bob", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        List<FormRow> ev = events();
        assertEquals(2, ev.size());
        ofType(ev, "DECISION_PROPOSED");
        ofType(ev, "DECISION_APPROVED");
        assertAllStamped(ev, AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theChainOfTheEventsAMoveWritesVerifies() {
        pendingAp();
        service("anna.grech", "Anna Grech").decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW);
        FormRowSet chain = new FormRowSet();
        chain.addAll(events());
        assertTrue("the move wrote more than one event", chain.size() > 1);
        FormDataDao vdao = mock(FormDataDao.class);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(chain);
        ChainVerifyService.Result r = new ChainVerifyService(vdao, ApprovalService.F_EVENT, "cmCase").verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(chain.size(), r.events);
    }
}
