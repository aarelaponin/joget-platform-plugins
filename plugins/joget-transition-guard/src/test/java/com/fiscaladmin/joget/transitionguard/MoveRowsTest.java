package com.fiscaladmin.joget.transitionguard;

import java.time.LocalDate;
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
 * The day of a move, the alert and the history entry (METHOD-2026-10-08-07, items 1 to 3).
 *
 * <p>A set-now field marked {@code @date} is written as the day alone. After a move it has
 * applied, the guard writes one row of the record each {@code notify} line names (the alert) and
 * of the record each {@code audit} line names (the history entry, beside the status event), with
 * the columns the line maps; a refused move writes neither. FormDataDao, MmConfigService and the
 * event writer are mocked; the state machine is real.
 */
public class MoveRowsTest {

    static final String AUDIT = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,justification=reason,value_before=from,value_after=to,"
            + "move=move,trigger_kind='user'";
    static final String NOTIFY = "close>attr:holder>case_closed>alert>patmin_alert>"
            + "addressed_to=recipient,template=template,about=attr:case_number,channel='email'";

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
        p.put("transitions", "close>closing>closed_on@date,closed_at>amount eq 0>");
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
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0",
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

    // ---------------------------------------------------------------- item 1: the day of a move

    @Test
    public void aFieldMarkedAsADateIsStampedWithTheDayAlone() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertEquals("OK: closing", result);
        assertEquals(LocalDate.now().toString(), record.getProperty("closed_on"));
        assertNull("the mark is not a column", record.getProperty("closed_on@date"));
        // a field not marked keeps the moment, date and time
        assertTrue(record.getProperty("closed_at"),
                record.getProperty("closed_at").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    // ---------------------------------------------------------------------- item 2: the alert

    @Test
    public void anAppliedMoveWritesOneAlertRow() {
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY);
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        FormRow alert = written("alert", "patmin_alert");
        assertFalse(alert.getId() == null || alert.getId().isEmpty());
        assertEquals("carmen.abela", alert.getProperty("addressed_to"));   // attr:holder
        assertEquals("case_closed", alert.getProperty("template"));
        assertEquals("DC-000001", alert.getProperty("about"));
        assertEquals("email", alert.getProperty("channel"));
    }

    @Test
    public void anAlertToARoleNamesTheRole() {
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY.replace("attr:holder", "role:senior"));
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertEquals("role:senior", written("alert", "patmin_alert").getProperty("addressed_to"));
    }

    @Test
    public void aRefusedMoveWritesNoAlertAndNoHistoryEntry() {
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY);
        p.put("audit", AUDIT);
        record.setProperty("amount", "250.00");
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        verify(dao, never()).saveOrUpdate(eq("alert"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("case_history"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aLineOfAnotherMoveWritesNothing() {
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY.replace("close>", "reopen>"));
        p.put("audit", AUDIT.replace("close>", "reopen>"));
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        verify(dao, never()).saveOrUpdate(eq("alert"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("case_history"), anyString(), any(FormRowSet.class));
    }

    // -------------------------------------------------------------- item 3: the history entry

    @Test
    public void anAppliedMoveWritesItsHistoryEntryBesideTheStatusEvent() {
        Map<String, String> p = trigger();
        p.put("audit", AUDIT);
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        verify(events).append(eq("case-uuid"), eq(StatusManager.EVENT_TYPE), eq("anna"),
                eq("open"), eq("closing"), eq("close: paid"), anyString());
        FormRow h = written("case_history", "patmin_history");
        assertEquals("DC-000001", h.getProperty("dcase"));
        assertEquals("anna", h.getProperty("made_by"));
        assertEquals("paid", h.getProperty("justification"));
        assertEquals("open", h.getProperty("value_before"));
        assertEquals("closing", h.getProperty("value_after"));
        assertEquals("close", h.getProperty("move"));
        assertEquals("user", h.getProperty("trigger_kind"));
        assertTrue(h.getProperty("moment"),
                h.getProperty("moment").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void theEngineSurfaceWritesTheHistoryEntryAndTheAlertToo() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>closed_on@date>>|open_case>open>>>");
        p.put("audit", AUDIT);
        p.put("notify", NOTIFY);
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        String result = new TransitionGuard().run(p, dao, cfg, sm, mock(GroupLookup.class), "system");
        assertEquals("OK: closing", result);
        assertEquals(LocalDate.now().toString(), record.getProperty("closed_on"));
        assertEquals("system", written("case_history", "patmin_history").getProperty("made_by"));
        assertEquals("carmen.abela", written("alert", "patmin_alert").getProperty("addressed_to"));
    }
}
