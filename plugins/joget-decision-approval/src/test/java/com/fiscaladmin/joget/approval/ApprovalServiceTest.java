package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ApprovalService} routing/decision logic. The dao, event writer and the
 * effect registry are mocked; the internal StatusManager guard is satisfied by mocking the
 * mmEntityTransition reads so Pending→terminal is permitted.
 */
public class ApprovalServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 4, 10, 0);

    private FormDataDao dao;
    private CaseEventWriter events;
    private Map<String, DecisionEffect> effects;

    private static FormRowSet set(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rs) s.add(r);
        return s;
    }

    private static FormRow band(String level, String bodyType, String quorum, String min, String max) {
        FormRow r = new FormRow();
        r.setProperty("level", level);
        r.setProperty("bodyType", bodyType);
        r.setProperty("quorum", quorum);
        r.setProperty("amountMin", min);
        r.setProperty("amountMax", max);
        return r;
    }

    private static FormRow transition(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    @Before
    public void setup() {
        dao = mock(FormDataDao.class);
        events = mock(CaseEventWriter.class);
        effects = new HashMap<String, DecisionEffect>();
        effects.put("WRITE_OFF", (entity, recordId, actor, now) -> "written off " + recordId);
        // StatusManager guard: DEFAULT scope has rules and permits Pending -> each terminal
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any()))
                .thenReturn(1L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(),
                anyString(), any(), any(), any()))
                .thenReturn(set(transition("Pending", "Approved"),
                        transition("Pending", "Rejected"),
                        transition("Pending", "Returned")));
        // no live duplicate by default
        when(dao.find(eq("cmApproval"), eq("cmApproval"),
                contains("recordId"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FormRowSet());
        // COI: cmCase load returns null -> COI checks short-circuit to "not barred"
        when(dao.load(eq("cmCase"), eq("cmCase"), anyString())).thenReturn(null);
    }

    private ApprovalService svc() {
        return new ApprovalService(dao, events, effects);
    }

    @Test
    public void request_noBandMatches_autoPassesAndRunsEffect() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet()); // no bands
        String r = svc().request("dmWriteOff", "W1", "WRITE_OFF", 100, "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("AUTO"));
        assertTrue(r.contains("written off W1"));
    }

    @Test
    public void request_bandMatches_createsPendingRequest() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(set(band("MANAGER", "", "", "0", "")));
        String r = svc().request("dmWriteOff", "W1", "WRITE_OFF", 5000, "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("PENDING"));
        verify(dao, atLeastOnce()).saveOrUpdate(eq("cmApproval"), eq("cmApproval"), any(FormRowSet.class));
    }

    private FormRow pendingAp(String kind, String requiredLevel) {
        FormRow ap = new FormRow();
        ap.setId("AP-1");
        ap.setProperty("status", "Pending");
        ap.setProperty("entity", "dmWriteOff");
        ap.setProperty("recordId", "W1");
        ap.setProperty("actionType", "WRITE_OFF");
        ap.setProperty("caseId", "C1");
        ap.setProperty("requestedBy", "alice");
        ap.setProperty("routeKind", kind);
        ap.setProperty("requiredLevel", requiredLevel);
        ap.setProperty("voters", "");
        ap.setProperty("quorum", "1");
        ap.setProperty("approvalsCount", "0");
        return ap;
    }

    @Test
    public void decide_singleApproveBySufficientRank_finalizesAndRunsEffect() {
        FormRow ap = pendingAp("SINGLE", "MANAGER");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r = svc().decide("AP-1", "bob", "DIRECTOR", "approve", "sound grounds", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        assertTrue(r.contains("written off W1"));
        assertEquals("Approved", ap.getProperty("status"));
    }

    @Test
    public void decide_rankTooLow_isBlocked() {
        FormRow ap = pendingAp("SINGLE", "MANAGER");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r = svc().decide("AP-1", "bob", "OFFICER", "approve", "x", NOW);
        assertTrue(r, r.startsWith("rank too low"));
        assertEquals("Pending", ap.getProperty("status"));
    }

    @Test
    public void decide_approverIsRequester_sodBlocked() {
        FormRow ap = pendingAp("SINGLE", "MANAGER");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r = svc().decide("AP-1", "alice", "DIRECTOR", "approve", "x", NOW);
        assertTrue(r, r.startsWith("SoD"));
    }

    @Test
    public void decide_reject_finalizesRejected() {
        FormRow ap = pendingAp("SINGLE", "MANAGER");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r = svc().decide("AP-1", "bob", "DIRECTOR", "reject", "not eligible", NOW);
        assertEquals("REJECTED", r);
        assertEquals("Rejected", ap.getProperty("status"));
    }

    @Test
    public void decide_blankReason_required() {
        FormRow ap = pendingAp("SINGLE", "MANAGER");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        assertEquals("reason required", svc().decide("AP-1", "bob", "DIRECTOR", "approve", "  ", NOW));
    }

    @Test
    public void decide_chain_advancesUntilLastStep() {
        FormRow ap = pendingAp("CHAIN", "SUPERVISOR");
        ap.setProperty("chain", "SUPERVISOR,MANAGER");
        ap.setProperty("currentStep", "0");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r = svc().decide("AP-1", "sup", "SUPERVISOR", "approve", "step1", NOW);
        assertTrue(r, r.contains("chain advanced to step 2/2"));
        assertEquals("Pending", ap.getProperty("status")); // not yet complete
    }

    @Test
    public void decide_quorum_progressesThenCompletes() {
        FormRow ap = pendingAp("QUORUM", "MANAGER");
        ap.setProperty("quorum", "2");
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        String r1 = svc().decide("AP-1", "m1", "MANAGER", "approve", "vote1", NOW);
        assertTrue(r1, r1.contains("quorum 1/2"));
        assertEquals("Pending", ap.getProperty("status"));
    }
}
