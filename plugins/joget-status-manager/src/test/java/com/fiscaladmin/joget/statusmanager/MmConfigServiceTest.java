package com.fiscaladmin.joget.statusmanager;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MmConfigService} — the metamodel reader. Verifies the
 * boolean/set-extraction logic; the dao is mocked.
 */
public class MmConfigServiceTest {

    private static FormRow state(String code) {
        FormRow r = new FormRow();
        r.setProperty("code", code);
        return r;
    }

    private static FormRowSet rows(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rs) s.add(r);
        return s;
    }

    @Test
    public void entityScopeHasRules_trueWhenCountPositive() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any()))
                .thenReturn(3L);
        assertTrue(new MmConfigService(dao).entityScopeHasRules("e", "VAT"));
    }

    @Test
    public void entityScopeHasRules_falseWhenZeroOrNull() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.count(anyString(), anyString(), anyString(), any())).thenReturn(0L);
        assertFalse(new MmConfigService(dao).entityScopeHasRules("e", "VAT"));
        when(dao.count(anyString(), anyString(), anyString(), any())).thenReturn(null);
        assertFalse(new MmConfigService(dao).entityScopeHasRules("e", "VAT"));
    }

    @Test
    public void terminalStateCodes_collectsCodeColumn() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.find(eq("mmState"), eq("mmState"), anyString(), any(), anyString(),
                any(), isNull(), isNull())).thenReturn(rows(state("CLOSED"), state("REJECTED")));
        Set<String> codes = new MmConfigService(dao).terminalStateCodes("DEBT");
        assertEquals(2, codes.size());
        assertTrue(codes.contains("CLOSED"));
        assertTrue(codes.contains("REJECTED"));
    }

    @Test
    public void caseType_returnsNullWhenAbsent() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.find(eq("mmCaseType"), eq("mmCaseType"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
        assertNull(new MmConfigService(dao).caseType("NOPE"));
    }
}
