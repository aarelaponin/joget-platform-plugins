package com.fiscaladmin.joget.eventchain;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CaseEventWriter} — the tamper-evident append-only writer.
 * FormDataDao is mocked; the hash/escape/seq logic is pure and asserted exactly.
 */
public class CaseEventWriterTest {

    @Test
    public void sha256_matchesKnownVector() {
        // SHA-256("") is a fixed, well-known digest — pins the algorithm.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                CaseEventWriter.sha256(""));
    }

    @Test
    public void esc_escapesJsonHostileChars() {
        assertEquals("a\\\\b\\\"c\\nd", CaseEventWriter.esc("a\\b\"c\nd"));
        assertEquals("", CaseEventWriter.esc(null));
    }

    @Test
    public void parseSeq_parsesOrDefaultsToZero() {
        assertEquals(42L, CaseEventWriter.parseSeq("0000000042"));
        assertEquals(0L, CaseEventWriter.parseSeq("not-a-number"));
    }

    @Test
    public void append_genesisRow_hasSeqZeroEmptyPrevHashAndConsistentHash() {
        FormDataDao dao = mock(FormDataDao.class);
        // no prior events -> genesis
        when(dao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());

        CaseEventWriter w = new CaseEventWriter(dao, "cmEvent");
        String hash = w.append("C1", "OPEN", "alice", "", "OPEN", "created", null);

        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao).saveOrUpdate(eq("cmEvent"), eq("cmEvent"), cap.capture());
        FormRow row = cap.getValue().get(0);

        assertEquals("C1", row.getProperty("caseId"));
        assertEquals("0000000000", row.getProperty("seq"));
        assertEquals("", row.getProperty("prevHash"));
        assertEquals("returned hash is the stored hash", hash, row.getProperty("hash"));
        // hash == sha256(payload + prevHash) with prevHash="" for genesis
        assertEquals(CaseEventWriter.sha256(row.getProperty("payload") + ""), hash);
        assertTrue("payload carries the reason", row.getProperty("payload").contains("\"reason\":\"created\""));
    }

    @Test
    public void append_secondEvent_chainsOnLastHashAndIncrementsSeq() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
        CaseEventWriter w = new CaseEventWriter(dao, "cmEvent");

        String h0 = w.append("C1", "OPEN", "a", "", "OPEN", "r0", null);
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        String h1 = w.append("C1", "MOVE", "a", "OPEN", "REVIEW", "r1", null);

        verify(dao, times(2)).saveOrUpdate(anyString(), anyString(), cap.capture());
        FormRow second = cap.getAllValues().get(1).get(0);
        assertEquals("second row seq increments", "0000000001", second.getProperty("seq"));
        assertEquals("second row chains on the first hash", h0, second.getProperty("prevHash"));
        assertEquals(CaseEventWriter.sha256(second.getProperty("payload") + h0), h1);
    }

    @Test
    public void eventFormId_isRequired() {
        FormDataDao dao = mock(FormDataDao.class);
        try {
            new CaseEventWriter(dao, null);
            fail("a null eventFormId must be refused, not silently defaulted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("eventFormId is required"));
        }
        try {
            new CaseEventWriter(dao, "   ");
            fail("a blank eventFormId must be refused, not silently defaulted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("eventFormId is required"));
        }
    }

    /**
     * REGRESSION — the 3–11 August 2026 cross-bundle collision.
     *
     * <p>This library bundle is shared: two consumer bundles in one JVM see one
     * {@code CaseEventWriter} class. When the aim lived in a static, the last
     * Activator to start won for the whole process and CMBB's case events were
     * appended to tax registration's chain. Two writers with different targets,
     * alive at the same time, must write to their own carriers and to nothing else.
     */
    @Test
    public void twoWritersInOneJvm_doNotCross() {
        FormDataDao cmDao = mock(FormDataDao.class);
        FormDataDao regDao = mock(FormDataDao.class);
        when(cmDao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
        when(regDao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());

        // Constructed in the order the bundles started on jdx9: CMBB first (8 July),
        // then the registration guard (3 August) — the order that used to lose CMBB.
        CaseEventWriter cmbb = new CaseEventWriter(cmDao, "cmEvent");
        CaseEventWriter registration = new CaseEventWriter(regDao, "statusEvent");

        assertEquals("cmEvent", cmbb.getEventFormId());
        assertEquals("a later writer does not re-aim an earlier one",
                "statusEvent", registration.getEventFormId());

        // Interleave the appends — a later construction must not retarget a live writer.
        cmbb.append("case-1", "CASE_CREATED", "alice", "", "OPEN", "created", null);
        registration.append("reg-1", "CASE_CREATED", "bob", "", "OPEN", "created", null);
        cmbb.append("case-1", "CASE_CLOSED", "alice", "OPEN", "CLOSED", "done", null);

        verify(cmDao, times(2)).saveOrUpdate(eq("cmEvent"), eq("cmEvent"), any(FormRowSet.class));
        verify(regDao, times(1)).saveOrUpdate(eq("statusEvent"), eq("statusEvent"), any(FormRowSet.class));
        // The whole incident in one assertion: no CMBB row ever reached statusEvent.
        verify(cmDao, never()).saveOrUpdate(eq("statusEvent"), anyString(), any(FormRowSet.class));
        verify(regDao, never()).saveOrUpdate(eq("cmEvent"), anyString(), any(FormRowSet.class));
    }

    /** Each writer keeps its own seq/hash cursor — chains do not interleave. */
    @Test
    public void twoWritersInOneJvm_keepSeparateChains() {
        FormDataDao cmDao = mock(FormDataDao.class);
        FormDataDao regDao = mock(FormDataDao.class);
        when(cmDao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
        when(regDao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());

        CaseEventWriter cmbb = new CaseEventWriter(cmDao, "cmEvent");
        CaseEventWriter registration = new CaseEventWriter(regDao, "statusEvent");

        cmbb.append("case-1", "A", "u", "", "S1", "r", null);
        registration.append("reg-1", "A", "u", "", "S1", "r", null);
        cmbb.append("case-1", "B", "u", "S1", "S2", "r", null);

        ArgumentCaptor<FormRowSet> cm = ArgumentCaptor.forClass(FormRowSet.class);
        verify(cmDao, times(2)).saveOrUpdate(anyString(), anyString(), cm.capture());
        assertEquals("0000000000", cm.getAllValues().get(0).get(0).getProperty("seq"));
        assertEquals("CMBB's seq is not advanced by the registration writer",
                "0000000001", cm.getAllValues().get(1).get(0).getProperty("seq"));

        ArgumentCaptor<FormRowSet> reg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(regDao).saveOrUpdate(anyString(), anyString(), reg.capture());
        assertEquals("the registration chain starts at its own genesis",
                "0000000000", reg.getValue().get(0).getProperty("seq"));
        assertEquals("", reg.getValue().get(0).getProperty("prevHash"));
    }
}
