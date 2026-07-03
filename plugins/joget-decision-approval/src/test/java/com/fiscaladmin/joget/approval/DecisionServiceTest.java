package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DecisionService} — authority rank / collegial quorum / reasoned-grounds
 * decision logic. dao and event writer are mocked.
 */
public class DecisionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 4, 10, 0);

    private FormRow decision(String... kv) {
        FormRow r = new FormRow();
        r.setId("D1");
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private FormRow authRow(String level, String quorum, String min, String max) {
        FormRow r = new FormRow();
        r.setProperty("level", level);
        r.setProperty("quorum", quorum);
        r.setProperty("amountMin", min);
        r.setProperty("amountMax", max);
        return r;
    }

    private FormDataDao daoFor(FormRow d, FormRowSet authRows) {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.load(eq("cmDecision"), eq("cmDecision"), eq("D1"))).thenReturn(d);
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), isNull(), isNull())).thenReturn(authRows);
        return dao;
    }

    private static FormRowSet set(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rs) s.add(r);
        return s;
    }

    @Test
    public void rank_ordersKnownLevelsAndZeroForUnknown() {
        assertTrue(DecisionService.rank("MANAGER") > DecisionService.rank("OFFICER"));
        assertEquals(0, DecisionService.rank("bogus"));
        assertEquals("DIRECTOR", DecisionService.nextRank("MANAGER"));
        assertEquals("COMMISSIONER", DecisionService.nextRank("COMMISSIONER")); // caps at top
    }

    @Test
    public void decide_missingReasons_rejects() {
        FormRow d = decision("actionType", "WRITE_OFF", "amount", "100",
                "approverLevel", "MANAGER", "reasons", "");
        DecisionService svc = new DecisionService(daoFor(d, set()), mock(CaseEventWriter.class));
        String result = svc.decide("D1", "alice", NOW);
        assertTrue(result.startsWith("REJECTED"));
        assertTrue(result.contains("grounds"));
        assertEquals("REJECTED", d.getProperty("decisionStatus"));
    }

    @Test
    public void decide_sufficientAuthority_approves() {
        FormRow d = decision("actionType", "WRITE_OFF", "amount", "5000",
                "approverLevel", "DIRECTOR", "reasons", "irrecoverable");
        FormRowSet auth = set(authRow("MANAGER", "0", "0", "10000"));
        DecisionService svc = new DecisionService(daoFor(d, auth), mock(CaseEventWriter.class));
        String result = svc.decide("D1", "dir", NOW);
        assertTrue(result, result.startsWith("APPROVED"));
        assertEquals("APPROVED", d.getProperty("decisionStatus"));
    }

    @Test
    public void decide_approverBelowRequiredLevel_rejects() {
        FormRow d = decision("actionType", "WRITE_OFF", "amount", "5000",
                "approverLevel", "OFFICER", "reasons", "x");
        FormRowSet auth = set(authRow("MANAGER", "0", "0", "10000"));
        DecisionService svc = new DecisionService(daoFor(d, auth), mock(CaseEventWriter.class));
        String result = svc.decide("D1", "o", NOW);
        assertTrue(result, result.startsWith("REJECTED"));
        assertTrue(result.contains("below required"));
    }

    @Test
    public void decide_collegialQuorumNotMet_rejects() {
        FormRow d = decision("actionType", "WAIVER", "amount", "5000",
                "approverLevel", "MANAGER", "reasons", "x",
                "bodyType", "COLLEGIAL", "approvalsCount", "1");
        FormRowSet auth = set(authRow("MANAGER", "3", "0", "10000"));
        DecisionService svc = new DecisionService(daoFor(d, auth), mock(CaseEventWriter.class));
        String result = svc.decide("D1", "m", NOW);
        assertTrue(result, result.startsWith("REJECTED"));
        assertTrue(result.contains("quorum"));
    }

    @Test
    public void decide_alreadyProcessed_isNoOp() {
        FormRow d = decision("result", "APPROVED earlier");
        DecisionService svc = new DecisionService(daoFor(d, set()), mock(CaseEventWriter.class));
        assertEquals("no-op: already processed", svc.decide("D1", "a", NOW));
    }

    @Test
    public void hasApprovedDecision_reflectsCount() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.count(eq("cmDecision"), eq("cmDecision"), anyString(), any())).thenReturn(1L);
        assertTrue(new DecisionService(dao, mock(CaseEventWriter.class)).hasApprovedDecision("C1"));
        when(dao.count(eq("cmDecision"), eq("cmDecision"), anyString(), any())).thenReturn(0L);
        assertFalse(new DecisionService(dao, mock(CaseEventWriter.class)).hasApprovedDecision("C1"));
    }
}
