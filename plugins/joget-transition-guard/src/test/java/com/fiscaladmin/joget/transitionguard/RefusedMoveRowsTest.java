package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
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
 * What a refused move writes (METHOD-2026-10-12-14; the owner's ruling of 11 October 2026 on
 * round 1, card 4, for the alternative: "a refused move writes the lines marked for refusal").
 *
 * <p>The debt management engagement's UC-DM-10-T06 asks for a history entry when an escalation
 * is refused, and UC-DM-18-T08 for an alert to the officer when a closure is refused. The guard's
 * {@code refused} property carries the lines the model marks for a refusal, each {@code audit>}
 * or {@code notify>} followed by a line of the form the {@code audit} and {@code notify}
 * properties use. After a refused move the guard writes those that name the move; after an
 * applied move, none of them. FormDataDao, MmConfigService and the event writer are mocked; the
 * state machine is real.
 */
public class RefusedMoveRowsTest {

    static final String HISTORY = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,justification=reason,value_before=from,value_after=to,"
            + "move=move,trigger_kind='refused'";
    static final String ALERT = "close>attr:holder>closure_refused>alert>patmin_alert>"
            + "addressed_to=recipient,template=template,about=attr:case_number,channel='internal'";
    static final String REFUSED = "audit>" + HISTORY + "\nnotify>" + ALERT;

    private FormDataDao dao;
    private MmConfigService cfg;
    private CaseEventWriter events;
    private StatusManager sm;
    private FormRow act;
    private FormRow record;

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

    /** A trigger form's properties: the move `close`, refused while the amount is not 0. */
    private static Map<String, String> trigger() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "close>closing>closed_on@date>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actDcaseClose");
        p.put("actTable", "patmin_case_act");
        p.put("checkRoles", "true");
        p.put("reasonField", "reason");
        p.put("copyFields", "");
        p.put("refused", REFUSED);
        return p;
    }

    private static GroupLookup holds(String... groups) {
        return username -> Arrays.asList(groups);
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        events = mock(CaseEventWriter.class);
        sm = new StatusManager(dao, cfg, events);
        act = row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "asked twice");
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "250.00",
                "holder", "carmen.abela");
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        FormRow move = row("m1", "fromStatus", "open", "toStatus", "closing",
                "transitionCode", "close", "roles", "officer,senior");
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(move));
    }

    /** The one row written to {@code table}, or a failure naming how many were. */
    private FormRow written(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        List<FormRowSet> all = rows.getAllValues();
        assertEquals("one save to " + table, 1, all.size());
        assertEquals("one row in it", 1, all.get(0).size());
        return all.get(0).get(0);
    }

    // ----------------------------------------------------- the person's path (a trigger form)

    @Test
    public void aRefusedMoveWritesTheHistoryEntryMarkedForARefusal() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertEquals("the record did not move", "open", record.getProperty("state"));
        FormRow h = written("case_history", "patmin_history");
        assertEquals("DC-000001", h.getProperty("dcase"));
        assertEquals("anna", h.getProperty("made_by"));
        assertEquals("asked twice", h.getProperty("justification"));
        assertEquals("close", h.getProperty("move"));
        assertEquals("refused", h.getProperty("trigger_kind"));
        // nothing moved: the state it left and the state it is in are the same
        assertEquals("open", h.getProperty("value_before"));
        assertEquals("open", h.getProperty("value_after"));
        assertTrue(h.getProperty("moment"),
                h.getProperty("moment").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        // a refused move has no status event and no save of the record
        verify(events, never()).append(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aRefusedMoveWritesTheAlertMarkedForARefusal() {
        new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        FormRow a = written("alert", "patmin_alert");
        assertEquals("carmen.abela", a.getProperty("addressed_to"));      // attr:holder
        assertEquals("closure_refused", a.getProperty("template"));
        assertEquals("DC-000001", a.getProperty("about"));
        assertEquals("internal", a.getProperty("channel"));
    }

    @Test
    public void aMoveRefusedForTheActorsRoleWritesTheLinesToo() {
        record.setProperty("amount", "0");                                 // the guard would pass
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("clerk"), "bob");
        assertTrue(result, result.startsWith("REJECTED: ") && result.contains("roles"));
        assertEquals("bob", written("case_history", "patmin_history").getProperty("made_by"));
        assertEquals("carmen.abela", written("alert", "patmin_alert").getProperty("addressed_to"));
    }

    @Test
    public void anAppliedMoveWritesNoLineMarkedForARefusal() {
        record.setProperty("amount", "0");
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertEquals("OK: closing", result);
        verify(dao, never()).saveOrUpdate(eq("case_history"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("alert"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aRefusedMoveStillWritesNoLineOfAnAppliedMove() {
        Map<String, String> p = trigger();
        p.remove("refused");
        p.put("audit", HISTORY);
        p.put("notify", ALERT);
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        verify(dao, never()).saveOrUpdate(eq("case_history"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("alert"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aRefusedLineOfAnotherMoveWritesNothing() {
        Map<String, String> p = trigger();
        p.put("refused", REFUSED.replace(">close>", ">reopen>"));
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        verify(dao, never()).saveOrUpdate(eq("case_history"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("alert"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aRowNotWrittenIsSaidAndTheRefusalStands() {
        doThrow(new RuntimeException("no such table")).when(dao)
                .saveOrUpdate(eq("case_history"), eq("patmin_history"), any(FormRowSet.class));
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains("history entry NOT written: no such table"));
        // the alert is still written, and the act carries what came of it
        assertEquals("carmen.abela", written("alert", "patmin_alert").getProperty("addressed_to"));
        assertEquals(result, act.getProperty(TransitionGuard.RESULT_FIELD));
    }

    // ----------------------------------------------- the engine surface (the engagement's runner)

    @Test
    public void theEngineSurfaceWritesTheLinesOfARefusedMoveAndKeepsTheResult() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>closed_on@date>amount eq 0>|open_case>open>>>");
        p.put("refused", REFUSED);
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        String result = new TransitionGuard().run(p, dao, cfg, sm, mock(GroupLookup.class), "system");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertEquals("open", record.getProperty("state"));
        assertEquals(result, record.getProperty(TransitionGuard.RESULT_FIELD));
        assertEquals("", record.getProperty("lc_action"));
        FormRow h = written("case_history", "patmin_history");
        assertEquals("system", h.getProperty("made_by"));
        assertEquals("open", h.getProperty("value_after"));
        assertEquals("carmen.abela", written("alert", "patmin_alert").getProperty("addressed_to"));
    }
}
