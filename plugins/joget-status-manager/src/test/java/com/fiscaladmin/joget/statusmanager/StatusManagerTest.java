package com.fiscaladmin.joget.statusmanager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatusManager} — the config-driven entity state machine.
 * MmConfigService and CaseEventWriter are mocked; the scope-resolution and guard
 * logic is asserted exactly, and the write/audit side-effects are verified.
 */
public class StatusManagerTest {

    private static FormRow tr(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    private static FormRowSet rows(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rs) s.add(r);
        return s;
    }

    @Test
    public void effectiveChain_dedupesAndAlwaysEndsWithDefault() {
        List<String> chain = StatusManager.effectiveChain(Arrays.asList("VAT", "", "VAT", "CIT"));
        assertEquals(Arrays.asList("VAT", "CIT", "DEFAULT"), chain);
        assertEquals(Collections.singletonList("DEFAULT"),
                StatusManager.effectiveChain(null));
    }

    @Test
    public void resolveScope_picksMostSpecificScopeThatHasRules() {
        FormDataDao dao = mock(FormDataDao.class);
        MmConfigService cfg = mock(MmConfigService.class);
        // VAT scope has no rules; CIT does -> CIT wins over DEFAULT
        when(cfg.entityScopeHasRules("dmDebt", "VAT")).thenReturn(false);
        when(cfg.entityScopeHasRules("dmDebt", "CIT")).thenReturn(true);
        StatusManager sm = new StatusManager(dao, cfg, mock(CaseEventWriter.class));
        assertEquals("CIT", sm.resolveScope("dmDebt", Arrays.asList("VAT", "CIT")));
    }

    @Test
    public void resolveScope_fallsBackToDefaultWhenNoScopeHasRules() {
        MmConfigService cfg = mock(MmConfigService.class);
        when(cfg.entityScopeHasRules(anyString(), anyString())).thenReturn(false);
        StatusManager sm = new StatusManager(mock(FormDataDao.class), cfg, mock(CaseEventWriter.class));
        assertEquals("DEFAULT", sm.resolveScope("e", Arrays.asList("A", "B")));
    }

    @Test
    public void canTransition_and_validNext_readTheResolvedScope() {
        MmConfigService cfg = mock(MmConfigService.class);
        when(cfg.entityScopeHasRules("e", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("e", "DEFAULT"))
                .thenReturn(rows(tr("DRAFT", "ACTIVE"), tr("ACTIVE", "CLOSED")));
        StatusManager sm = new StatusManager(mock(FormDataDao.class), cfg, mock(CaseEventWriter.class));

        assertTrue(sm.canTransition("e", null, "DRAFT", "ACTIVE"));
        assertFalse(sm.canTransition("e", null, "DRAFT", "CLOSED"));
        assertEquals(java.util.Collections.singleton("ACTIVE"), sm.validNext("e", null, "DRAFT"));
    }

    @Test
    public void transition_legalMove_writesStatusAndAppendsEvent() {
        FormDataDao dao = mock(FormDataDao.class);
        MmConfigService cfg = mock(MmConfigService.class);
        CaseEventWriter events = mock(CaseEventWriter.class);
        FormRow rec = new FormRow();
        rec.setId("R1");
        rec.setProperty("status", "DRAFT");
        when(dao.load("dmInst", "dmInst", "R1")).thenReturn(rec);
        when(cfg.entityScopeHasRules("dmInst", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("dmInst", "DEFAULT")).thenReturn(rows(tr("DRAFT", "ACTIVE")));

        StatusManager sm = new StatusManager(dao, cfg, events);
        sm.transition("dmInst", "dmInst", "status", "R1", "C1", "ACTIVE", null, "officer", "activate");

        assertEquals("ACTIVE", rec.getProperty("status"));
        verify(dao).saveOrUpdate(eq("dmInst"), eq("dmInst"), any(FormRowSet.class));
        verify(events).append(eq("C1"), eq("STATUS_CHANGED"), eq("officer"),
                eq("DRAFT"), eq("ACTIVE"), eq("activate"), contains("\"entity\":\"dmInst\""));
    }

    @Test
    public void transition_illegalMove_throwsAndWritesNothing() {
        FormDataDao dao = mock(FormDataDao.class);
        MmConfigService cfg = mock(MmConfigService.class);
        CaseEventWriter events = mock(CaseEventWriter.class);
        FormRow rec = new FormRow();
        rec.setId("R1");
        rec.setProperty("status", "DRAFT");
        when(dao.load(anyString(), anyString(), eq("R1"))).thenReturn(rec);
        when(cfg.entityScopeHasRules(anyString(), anyString())).thenReturn(true);
        when(cfg.entityTransitions(anyString(), anyString())).thenReturn(rows(tr("DRAFT", "ACTIVE")));

        StatusManager sm = new StatusManager(dao, cfg, events);
        try {
            sm.transition("e", "e", "status", "R1", "C1", "CLOSED", null, "o", "r");
            fail("expected InvalidTransitionException");
        } catch (InvalidTransitionException ex) {
            assertEquals("DRAFT", ex.getFromState());
            assertEquals("CLOSED", ex.getToState());
        }
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
        verify(events, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void apply_setsStatusOnRowButDoesNotSave_callerPersists() {
        MmConfigService cfg = mock(MmConfigService.class);
        CaseEventWriter events = mock(CaseEventWriter.class);
        FormDataDao dao = mock(FormDataDao.class);
        when(cfg.entityScopeHasRules(anyString(), anyString())).thenReturn(true);
        when(cfg.entityTransitions(anyString(), anyString())).thenReturn(rows(tr("OPEN", "DONE")));
        FormRow row = new FormRow();
        row.setId("X");
        row.setProperty("st", "OPEN");

        new StatusManager(dao, cfg, events)
                .apply("e", row, "st", "C1", "DONE", null, "o", "finish");

        assertEquals("row status set for caller to persist", "DONE", row.getProperty("st"));
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
        verify(events).append(eq("C1"), eq("STATUS_CHANGED"), eq("o"),
                eq("OPEN"), eq("DONE"), eq("finish"), anyString());
    }
}
