package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The guard's first tests (METHOD-2026-09-25-04, items 3 and 5). A person's move is made on a
 * trigger form, created as a new act that names the record it moves; the guard moves THAT record,
 * and only for an actor holding one of the roles the move's mmEntityTransition row names. The
 * engine surface — the record's own form, posted to by engines through the data API — works as it
 * did. FormDataDao, MmConfigService and the event writer are mocked; the state machine is real.
 */
public class TransitionGuardTest {

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

    private static Map<String, String> trigger() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "close>closing>closed_at>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actDcaseClose");
        p.put("actTable", "patmin_case_act");
        p.put("checkRoles", "true");
        p.put("reasonField", "reason");
        p.put("copyFields", "");
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
        act = row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "paid");
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0");
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        FormRow move = row("m1", "fromStatus", "open", "toStatus", "closing",
                "transitionCode", "close", "roles", "officer,senior");
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(move));
    }

    @Test
    public void aTriggerFormMovesTheRecordItNamesForAnActorWithTheRole() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertEquals("OK: closing", result);
        assertEquals("closing", record.getProperty("state"));
        assertFalse(record.getProperty("closed_at").isEmpty());          // the set-now effect
        verify(dao).saveOrUpdate(eq("frmCase"), eq("patmin_case"), argThat(s -> s.contains(record)));
        assertEquals(result, act.getProperty("transition_result"));   // written on the act
        verify(dao).saveOrUpdate(eq("actDcaseClose"), eq("patmin_case_act"), argThat(s -> s.contains(act)));
        // the event names the move and its reason, on the record's own id
        verify(events).append(eq("case-uuid"), eq(StatusManager.EVENT_TYPE), eq("anna"),
                eq("open"), eq("closing"), eq("close: paid"), anyString());
    }

    @Test
    public void anActorHoldingNoneOfTheMovesRolesIsRefusedAndNothingMoves() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("clerk"), "carmen");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains("'carmen' holds none of the roles that may make this move (officer,senior)"));
        assertEquals("open", record.getProperty("state"));
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
        verify(events, never()).append(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        assertEquals(result, act.getProperty("transition_result"));   // the refusal is recorded
    }

    @Test
    public void anActorWithNoGroupsAtAllIsRefused() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm,
                username -> Collections.<String>emptyList(), "nobody");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertEquals("open", record.getProperty("state"));
    }

    @Test
    public void anActNamingNoRecordIsRefused() {
        when(dao.find(eq("frmCase"), eq("patmin_case"), anyString(), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(new FormRowSet());
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertEquals("REJECTED: no dcase record 'DC-000001' — the move is made from its record", result);
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void theMovesGuardStillHolds() {
        record.setProperty("amount", "250.00");
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: ") && result.contains("amount eq 0"));
        assertEquals("open", record.getProperty("state"));
    }

    @Test
    public void theMovesInputsAreCopiedOntoTheRecord() {
        Map<String, String> p = trigger();
        p.put("copyFields", "closure_reason");
        act.setProperty("closure_reason", "paid");
        new TransitionGuard().run(p, dao, cfg, sm, holds("senior"), "joseph");
        assertEquals("paid", record.getProperty("closure_reason"));
    }

    @Test
    public void theEngineSurfaceMovesItsOwnRowAsBefore() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>>>|open_case>open>>>");
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        GroupLookup groups = mock(GroupLookup.class);
        String result = new TransitionGuard().run(p, dao, cfg, sm, groups, "system");
        assertEquals("OK: closing", result);
        assertEquals("", record.getProperty("lc_action"));             // the action is consumed
        verifyNoInteractions(groups);                                  // the API credential gates it
        verify(dao).saveOrUpdate(eq("dcase"), eq("patmin_case"), argThat(s -> s.contains(record)));
    }
}
