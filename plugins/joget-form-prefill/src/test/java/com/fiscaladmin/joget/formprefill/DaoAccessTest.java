package com.fiscaladmin.joget.formprefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link FormPrefillLoadBinder.DaoAccess#findByField}, the one place the plugin reads the primary record.
 * A row's id is its key, not a custom property: a match on {@code id} reads the row by its key ({@code FormDataDao.load});
 * every other match field is read as before, through {@code customProperties} ({@code FormDataDao.find}).
 * The Joget {@link FormDataDao} is faked with Mockito; no Joget runtime.
 */
public class DaoAccessTest {

    private static FormRow row(String id, String... kv) {
        FormRow r = new FormRow();
        r.setId(id);
        for (int i = 0; i + 1 < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    // ---- a match on id: the row is read by its key ---------------------------------------------

    @Test
    public void matchOnId_readsTheRowByItsKey_found() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.load("m03", "m03", "7e6eb709-28a9")).thenReturn(row("7e6eb709-28a9", "name", "The Institute", "kind", "college"));

        List<Map<String, String>> rows = new FormPrefillLoadBinder.DaoAccess(dao).findByField("m03", "m03", "id", "7e6eb709-28a9");

        assertEquals("that one row", 1, rows.size());
        assertEquals("The Institute", rows.get(0).get("name"));
        assertEquals("college", rows.get(0).get("kind"));
        assertEquals("7e6eb709-28a9", rows.get(0).get("id"));
        verify(dao).load("m03", "m03", "7e6eb709-28a9");
        // the query on customProperties.id, which cannot be resolved, is never made
        verify(dao, never()).find(anyString(), anyString(), anyString(), any(Object[].class), any(), any(), any(), any());
    }

    @Test
    public void matchOnId_readsTheRowByItsKey_notFound() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.load("m03", "m03", "no-such-id")).thenReturn(null);

        List<Map<String, String>> rows = new FormPrefillLoadBinder.DaoAccess(dao).findByField("m03", "m03", "id", "no-such-id");

        assertTrue("none", rows.isEmpty());
        verify(dao).load("m03", "m03", "no-such-id");
        verify(dao, never()).find(anyString(), anyString(), anyString(), any(Object[].class), any(), any(), any(), any());
    }

    @Test
    public void matchOnId_aBlankKeyReadsNothing() {
        FormDataDao dao = mock(FormDataDao.class);

        assertTrue(new FormPrefillLoadBinder.DaoAccess(dao).findByField("m03", "m03", "id", "  ").isEmpty());
        assertTrue(new FormPrefillLoadBinder.DaoAccess(dao).findByField("m03", "m03", "id", null).isEmpty());
        verify(dao, never()).load(anyString(), anyString(), anyString());
        verify(dao, never()).find(anyString(), anyString(), anyString(), any(Object[].class), any(), any(), any(), any());
    }

    // ---- any other match field: still read through customProperties ----------------------------

    @Test
    public void matchOnACustomProperty_isStillReadThroughCustomProperties() {
        FormDataDao dao = mock(FormDataDao.class);
        FormRowSet found = new FormRowSet();
        found.add(row("r1", "tin", "100525M", "state", "OPEN"));
        found.add(row("r2", "tin", "100525M", "state", "CLOSED"));
        when(dao.find(anyString(), anyString(), anyString(), any(Object[].class), any(), any(), any(), any())).thenReturn(found);

        List<Map<String, String>> rows = new FormPrefillLoadBinder.DaoAccess(dao).findByField("cmCase", "cmCase", "tin", "100525M");

        assertEquals(2, rows.size());
        assertEquals("OPEN", rows.get(0).get("state"));
        assertEquals("r2", rows.get(1).get("id"));
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).find(eq("cmCase"), eq("cmCase"), eq("WHERE e.customProperties.tin = ?1"), params.capture(), any(), any(), eq(0), any());
        assertEquals("100525M", params.getValue()[0]);
        verify(dao, never()).load(anyString(), anyString(), anyString());
    }

    @Test
    public void matchOnACustomPropertyWhoseNameBeginsWithId_isNotTakenForTheKey() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.find(anyString(), anyString(), anyString(), any(Object[].class), any(), any(), any(), any())).thenReturn(new FormRowSet());

        List<Map<String, String>> rows = new FormPrefillLoadBinder.DaoAccess(dao).findByField("m05", "m05", "institution_id", "abc");

        assertTrue(rows.isEmpty());
        verify(dao).find(eq("m05"), eq("m05"), eq("WHERE e.customProperties.institution_id = ?1"), any(Object[].class), any(), any(), eq(0), any());
        verify(dao, never()).load(anyString(), anyString(), anyString());
    }
}
