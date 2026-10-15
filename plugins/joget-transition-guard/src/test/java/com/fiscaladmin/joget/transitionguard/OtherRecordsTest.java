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
 * A guard over another record's rows, and a set_attr on a related record (METHOD-2026-10-08-11,
 * items 4 and 5).
 *
 * <p>A guard term {@code none(<entity> where <condition>)} counts the rows of the record the
 * {@code records} property places, with {@code this} standing for the moved record's
 * {@code thisKey} (its row id when empty), and refuses the move where any is found; a term that
 * cannot be counted refuses it too. A line of {@code related} names rows of another record by
 * the same condition, and the guard writes them after a move it has applied. FormDataDao,
 * MmConfigService and the event writer are mocked; the state machine is real.
 */
public class OtherRecordsTest {

    static final String NONE = "none(action where dcase = this and standing not in (concluded, withdrawn))";
    static final String GUARD = "amount eq 0 and " + NONE;
    static final String COUNTED = "WHERE e.customProperties.dcase = ?1 AND "
            + "(e.customProperties.standing NOT IN (?2, ?3) OR e.customProperties.standing IS NULL)";
    static final String RECORDS = "action>frmAction>patmin_action\n"
            + "assignment>frmAssignment>patmin_assignment";
    static final String RELATED = "close>assignment>dcase = this and ended_on is null>"
            + "ended_on=moment,end_kind='closure'";
    static final String OPEN_ASSIGNMENT = "WHERE e.customProperties.dcase = ?1 AND "
            + "(e.customProperties.ended_on IS NULL OR e.customProperties.ended_on = '')";

    private FormDataDao dao;
    private MmConfigService cfg;
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

    /** A person's close of case DC-000001, by its trigger form. */
    private static Map<String, String> trigger() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "close>closing>>" + GUARD + ">");
        p.put("records", RECORDS);
        p.put("thisKey", "case_number");
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
        sm = new StatusManager(dao, cfg, mock(CaseEventWriter.class));
        act = row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "paid");
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0");
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(row("m1", "fromStatus", "open",
                "toStatus", "closing", "transitionCode", "close", "roles", "officer")));
    }

    private Object[] counted(long n) {
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).count(eq("frmAction"), eq("patmin_action"), eq(COUNTED), params.capture());
        return params.getValue();
    }

    // ------------------------------------------------- item 4: a guard over another record's rows

    @Test
    public void aCaseWithAnUnfinishedActionIsNotClosed() {
        when(dao.count(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class)))
                .thenReturn(1L);
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains(NONE));
        assertEquals("open", record.getProperty("state"));
        assertArrayEquals(new Object[]{"DC-000001", "concluded", "withdrawn"}, counted(1));
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aCaseWhoseActionsAreAllFinishedIsClosed() {
        when(dao.count(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class)))
                .thenReturn(0L);
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertEquals("OK: closing", result);
        assertArrayEquals(new Object[]{"DC-000001", "concluded", "withdrawn"}, counted(0));
    }

    @Test
    public void theRecordsOwnTermIsStillAskedFirst() {
        record.setProperty("amount", "250.00");
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: guard failed: amount eq 0"));
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
    }

    @Test
    public void aCountThatCannotBeMadeRefusesTheMove() {
        when(dao.count(anyString(), anyString(), anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("no such column"));
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains("could not be counted"));
        assertEquals("open", record.getProperty("state"));
    }

    @Test
    public void aTermOverARecordTheGuardIsNotToldOfRefusesTheMove() {
        Map<String, String> p = trigger();
        p.put("records", "assignment>frmAssignment>patmin_assignment");
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains("'action'"));
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
    }

    @Test
    public void withNoKeyThisIsTheRecordsRowId() {
        Map<String, String> p = trigger();
        p.put("thisKey", "");
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertArrayEquals(new Object[]{"case-uuid", "concluded", "withdrawn"}, counted(0));
    }

    // ------------------------------------------------- item 5: a set_attr on a related record

    /** The close by its trigger form, guarded over its own record only, ending the assignment:
     *  item 5 is proved apart from item 4. */
    private static Map<String, String> closing() {
        Map<String, String> p = trigger();
        p.put("transitions", "close>closing>>amount eq 0>");
        p.put("related", RELATED);
        return p;
    }

    private void anOpenAssignment(FormRow... rows) {
        when(dao.find(eq("frmAssignment"), eq("patmin_assignment"), eq(OPEN_ASSIGNMENT),
                any(Object[].class), isNull(), isNull(), isNull(), isNull())).thenReturn(set(rows));
    }

    @Test
    public void anAppliedMoveEndsTheCasesOpenAssignment() {
        Map<String, String> p = closing();
        FormRow a1 = row("as-1", "dcase", "DC-000001", "held_by", "carmen.abela");
        anOpenAssignment(a1);
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertEquals("OK: closing", result);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).find(eq("frmAssignment"), eq("patmin_assignment"), eq(OPEN_ASSIGNMENT),
                params.capture(), isNull(), isNull(), isNull(), isNull());
        assertArrayEquals(new Object[]{"DC-000001"}, params.getValue());
        ArgumentCaptor<FormRowSet> saved = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao).saveOrUpdate(eq("frmAssignment"), eq("patmin_assignment"), saved.capture());
        assertEquals(1, saved.getValue().size());
        FormRow w = saved.getValue().get(0);
        assertEquals("as-1", w.getId());
        assertTrue(w.getProperty("ended_on"),
                w.getProperty("ended_on").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals("closure", w.getProperty("end_kind"));
        assertEquals("carmen.abela", w.getProperty("held_by"));          // nothing else touched
        // the move's own save is made as well
        verify(dao).saveOrUpdate(eq("frmCase"), eq("patmin_case"), any(FormRowSet.class));
    }

    @Test
    public void aRefusedMoveEndsNoAssignment() {
        Map<String, String> p = closing();
        record.setProperty("amount", "250.00");
        new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        verify(dao, never()).find(eq("frmAssignment"), anyString(), anyString(), any(Object[].class),
                any(), any(), any(), any());
        verify(dao, never()).saveOrUpdate(eq("frmAssignment"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aRelatedRowNotWrittenIsSaidAndTheMoveStands() {
        Map<String, String> p = closing();
        anOpenAssignment(row("as-1", "dcase", "DC-000001"));
        doThrow(new RuntimeException("disk full")).when(dao)
                .saveOrUpdate(eq("frmAssignment"), anyString(), any(FormRowSet.class));
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("officer"), "anna");
        assertTrue(result, result.startsWith("OK: closing · assignment NOT written"));
        assertEquals("closing", record.getProperty("state"));
    }

    @Test
    public void theEngineSurfaceEndsTheAssignmentToo() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>>amount eq 0>|open_case>open>>>");
        p.put("records", RECORDS);
        p.put("thisKey", "case_number");
        p.put("related", RELATED);
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        anOpenAssignment(row("as-1", "dcase", "DC-000001"));
        String result = new TransitionGuard().run(p, dao, cfg, sm, mock(GroupLookup.class), "system");
        assertEquals("OK: closing", result);
        verify(dao).saveOrUpdate(eq("frmAssignment"), eq("patmin_assignment"), any(FormRowSet.class));
    }
}
