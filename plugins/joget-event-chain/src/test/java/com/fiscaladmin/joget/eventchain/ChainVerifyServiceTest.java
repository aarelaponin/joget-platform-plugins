package com.fiscaladmin.joget.eventchain;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChainVerifyService} — the read-only tamper detector.
 * Builds valid chains with the real hashing, then mutates them to prove each
 * defect class (seq gap, broken linkage, tampered payload) is caught.
 */
public class ChainVerifyServiceTest {

    /** Build a row exactly as CaseEventWriter would, with a correct hash. */
    private static FormRow row(long seq, String payload, String prevHash) {
        FormRow r = new FormRow();
        r.setProperty("seq", String.format("%010d", seq));
        r.setProperty("payload", payload);
        r.setProperty("prevHash", prevHash);
        r.setProperty("hash", CaseEventWriter.sha256(payload + prevHash));
        return r;
    }

    private static FormRowSet chainOf(FormRow... rows) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rows) s.add(r);
        return s;
    }

    private ChainVerifyService svcReturning(FormRowSet rows) {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(rows);
        return new ChainVerifyService(dao, "cmEvent", "cmCase");
    }

    @Test
    public void validChain_verifiesOk() {
        FormRow g = row(0, "{\"reason\":\"open\"}", "");
        FormRow e1 = row(1, "{\"reason\":\"move\"}", g.getProperty("hash"));
        ChainVerifyService.Result r = svcReturning(chainOf(g, e1)).verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(2, r.events);
        assertEquals(-1, r.firstBadSeq);
    }

    @Test
    public void emptyChain_isTriviallyIntact() {
        ChainVerifyService.Result r = svcReturning(new FormRowSet()).verify("C1");
        assertTrue(r.ok);
        assertEquals(0, r.events);
    }

    @Test
    public void tamperedPayload_isDetected() {
        FormRow g = row(0, "{\"reason\":\"open\"}", "");
        FormRow e1 = row(1, "{\"reason\":\"move\"}", g.getProperty("hash"));
        // mutate payload but leave the (now stale) hash -> recompute mismatches
        e1.setProperty("payload", "{\"reason\":\"TAMPERED\"}");
        ChainVerifyService.Result r = svcReturning(chainOf(g, e1)).verify("C1");
        assertFalse(r.ok);
        assertEquals(1, r.firstBadSeq);
        assertTrue(r.reason.contains("tampered"));
    }

    @Test
    public void seqGap_isDetected() {
        FormRow g = row(0, "{\"a\":1}", "");
        FormRow e2 = row(2, "{\"a\":2}", g.getProperty("hash")); // 1 is missing
        ChainVerifyService.Result r = svcReturning(chainOf(g, e2)).verify("C1");
        assertFalse(r.ok);
        assertEquals(2, r.firstBadSeq);
        assertTrue(r.reason.contains("seq gap"));
    }

    @Test
    public void brokenLinkage_isDetected() {
        FormRow g = row(0, "{\"a\":1}", "");
        // e1 claims a prevHash that isn't the genesis hash
        FormRow e1 = row(1, "{\"a\":2}", "deadbeef");
        ChainVerifyService.Result r = svcReturning(chainOf(g, e1)).verify("C1");
        assertFalse(r.ok);
        assertEquals(1, r.firstBadSeq);
        assertTrue(r.reason.contains("linkage"));
    }
}
