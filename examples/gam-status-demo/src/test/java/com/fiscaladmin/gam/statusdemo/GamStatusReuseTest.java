package com.fiscaladmin.gam.statusdemo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.InvalidTransitionException;
import com.fiscaladmin.joget.statusmanager.StatusManager;

/**
 * Reuse proof (behavioural). A NON-debt "gamWidget" entity, with its own
 * mmEntityState/mmEntityTransition rows, drives the PLATFORM StatusManager +
 * CaseEventWriter exactly as GamMoveGuard does live on jdx8. No debt/tax code is
 * involved; the platform classes come from the imported bundles.
 */
public class GamStatusReuseTest {

    private FormDataDao dao;
    private final Map<String, FormRow> widgets = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();

    private static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private static FormRowSet set(FormRow... rows) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rows) s.add(r);
        return s;
    }

    @Before
    public void setUp() {
        CaseEventWriter.setDefaultEventFormId("gamEvent");
        dao = mock(FormDataDao.class);

        widgets.put("W1", row("name", "Widget One", "status", "DRAFT"));
        when(dao.load(eq("gamWidget"), eq("gamWidget"), eq("W1")))
                .thenAnswer(i -> widgets.get("W1"));

        FormRowSet transitions = set(
                row("entity", "gamWidget", "scope", "DEFAULT", "fromStatus", "DRAFT", "toStatus", "ACTIVE"),
                row("entity", "gamWidget", "scope", "DEFAULT", "fromStatus", "ACTIVE", "toStatus", "CLOSED"));
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any()))
                .thenReturn(2L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(),
                anyString(), any(), any(), any())).thenReturn(transitions);
        when(dao.find(eq("mmEntityState"), eq("mmEntityState"), anyString(), any(),
                anyString(), any(), any(), any())).thenReturn(new FormRowSet());
        when(dao.find(eq("gamEvent"), eq("gamEvent"), anyString(), any(),
                anyString(), any(), any(), any())).thenReturn(new FormRowSet());

        doAnswer(i -> {
            FormRowSet rs = (FormRowSet) i.getArguments()[2];
            for (FormRow r : rs) events.add(r);
            return null;
        }).when(dao).saveOrUpdate(eq("gamEvent"), eq("gamEvent"), any(FormRowSet.class));
        doAnswer(i -> {
            FormRowSet rs = (FormRowSet) i.getArguments()[2];
            for (FormRow r : rs) widgets.put("W1", r);
            return null;
        }).when(dao).saveOrUpdate(eq("gamWidget"), eq("gamWidget"), any(FormRowSet.class));
    }

    @Test
    public void legalTransitionAdvancesStatusAndWritesEvent() {
        new StatusManager(dao).transition(
                "gamWidget", "gamWidget", "status", "W1", "W1", "ACTIVE", null, "tester", "legal move");
        assertEquals("ACTIVE", widgets.get("W1").getProperty("status"));
        assertEquals("one STATUS_CHANGED event appended", 1, events.size());
        assertEquals("STATUS_CHANGED", events.get(0).getProperty("eventType"));
        assertTrue("event payload names the entity",
                events.get(0).getProperty("payload").contains("gamWidget"));
    }

    @Test
    public void illegalTransitionIsRejectedAndNothingIsWritten() {
        widgets.get("W1").setProperty("status", "ACTIVE");
        try {
            new StatusManager(dao).transition(
                    "gamWidget", "gamWidget", "status", "W1", "W1", "DRAFT", null, "tester", "illegal move");
            fail("expected InvalidTransitionException for ACTIVE -> DRAFT");
        } catch (InvalidTransitionException expected) {
            // expected
        }
        assertEquals("status unchanged", "ACTIVE", widgets.get("W1").getProperty("status"));
        assertEquals("no event written on rejection", 0, events.size());
    }
}
