package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
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
 * An approval act withheld from its requester, and the approver's name fixed (METHOD-2026-10-16-01,
 * construct 2; MT-DM's request of 15 October 2026, item 2).
 *
 * <p>{@code withheld} names, for a move, the record's attribute holding who asked
 * ({@code move>attribute}): the guard refuses that person the move, whatever their roles. A set-now
 * field marked {@code @actor} ({@code approved_by@actor}) is written with who made the move, so that
 * the name is the act's own and never typed. FormDataDao, MmConfigService and the event writer are
 * mocked; the state machine is real.
 */
public class WithheldMoveTest {

    private FormDataDao dao;
    private MmConfigService cfg;
    private StatusManager sm;
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
        p.put("transitions", "approve_closure>closed>approved_by@actor>>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actDcaseApproveClosure");
        p.put("actTable", "patmin_case_act");
        p.put("checkRoles", "true");
        p.put("withheld", "approve_closure>requested_by");
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
        FormRow act = row("act-1", "lc_action", "approve_closure", "record_key", "DC-000001");
        record = row("case-uuid", "case_number", "DC-000001", "state", "closing",
                "requested_by", "anna.grech");
        when(dao.load("actDcaseApproveClosure", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(row("m1", "fromStatus", "closing",
                "toStatus", "closed", "transitionCode", "approve_closure", "roles", "senior")));
    }

    @Test
    public void theRequesterIsRefusedTheMoveWhateverTheirRoles() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("senior"), "Anna.Grech");
        assertTrue(result, result.startsWith("REJECTED: "));
        assertTrue(result, result.contains("requested_by"));
        assertEquals("closing", record.getProperty("state"));
        assertNull(record.getProperty("approved_by"));
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void anotherApproverMakesTheMoveAndTheMoveWritesWhoMadeIt() {
        String result = new TransitionGuard().run(trigger(), dao, cfg, sm, holds("senior"), "boris.vella");
        assertEquals("OK: closed", result);
        assertEquals("boris.vella", record.getProperty("approved_by"));
        assertNull("the mark is not a column", record.getProperty("approved_by@actor"));
    }

    @Test
    public void aMoveNotWithheldIsMadeByTheRequesterAsBefore() {
        Map<String, String> p = trigger();
        p.remove("withheld");
        String result = new TransitionGuard().run(p, dao, cfg, sm, holds("senior"), "anna.grech");
        assertEquals("OK: closed", result);
        assertEquals("anna.grech", record.getProperty("approved_by"));
    }

    @Test
    public void aRecordThatNamesNobodyWithholdsTheMoveFromNobody() {
        record.setProperty("requested_by", "");
        assertEquals("OK: closed",
                new TransitionGuard().run(trigger(), dao, cfg, sm, holds("senior"), "anna.grech"));
    }

    @Test
    public void theAttributeWithheldIsReadForTheMoveAskedForOnly() {
        assertEquals("requested_by", TransitionGuard.withheldAttr(
                "close>held_by\napprove_closure>requested_by", "approve_closure"));
        assertEquals("", TransitionGuard.withheldAttr("close>held_by", "approve_closure"));
        assertEquals("", TransitionGuard.withheldAttr("", "approve_closure"));
    }
}
