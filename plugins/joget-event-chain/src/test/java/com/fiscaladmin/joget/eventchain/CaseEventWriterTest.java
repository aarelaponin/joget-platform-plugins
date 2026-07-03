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
    public void defaultFormId_isNeutralAndConfigurable() {
        String original = CaseEventWriter.getDefaultEventFormId();
        try {
            assertEquals("caseEvent", CaseEventWriter.DEFAULT_EVENT_FORM);
            CaseEventWriter.setDefaultEventFormId("cmEvent");
            FormDataDao dao = mock(FormDataDao.class);
            assertEquals("no-arg ctor uses the configured default",
                    "cmEvent", new CaseEventWriter(dao).getEventFormId());
        } finally {
            CaseEventWriter.setDefaultEventFormId(original);
        }
    }
}
