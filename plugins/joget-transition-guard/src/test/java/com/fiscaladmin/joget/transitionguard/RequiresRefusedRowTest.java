package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
 * A refusal by {@code requires} that writes its history row (1.12.0; METHOD-2026-11-05-97, R55, G-e; UC-DM-10, 1b:
 * "The solution refuses ... changes nothing on the case, and records the refusal ... with who asked").
 *
 * <p>The move's requirement stands on the move page's root guard (joget-require-guard's RequireGuard, {@code exists}),
 * which refuses before the save, so the post-processor never runs and its {@code refused} rows were never written. For
 * a move that carries both a {@code requires} and a history entry written on its refusal, the kit gives MoveRefusal
 * the same line ({@code requires}); MoveRefusal judges it as RequireGuard judges that line, and where it fails writes
 * the {@code refused} rows with who asked, refuses the save, and leaves the words to the root guard, which has said
 * them already. The record moved is never saved. FormDataDao is mocked; the rows are the test's own.
 */
public class RequiresRefusedRowTest {

    static final String MESSAGE = "This case cannot be escalated: no final demand has been served on it.";
    /** MT-DM's line (a_v83_escalation_refused, v3), as the kit projects it on the root guard and on MoveRefusal. */
    static final String LINE = "lc_action|escalate_by_hand|recNotice|mtcadm_notice|debt_case|case_number|"
            + "e.customProperties.kind = 'final_demand'|" + MESSAGE;
    static final String COUNTED = "WHERE e.customProperties.debt_case = ?1 AND (e.customProperties.kind = 'final_demand')";
    static final String HISTORY = "escalate_by_hand>case_history>mtcadm_history>debt_case=attr:case_number,"
            + "made_by=actor,value_before=from,value_after=to,words=words,trigger_kind='refused'";

    private FormDataDao dao;
    private FormRow record;

    static final RowStamp.Who ANNA = new RowStamp.Who() {
        public String username() { return "anna"; }
        public String fullName() { return "Anna Grech"; }
    };

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

    /** MoveRefusal's properties as the kit projects them for escalate_by_hand, with `requires` where given. */
    private static Map<String, String> props(String guard, String requires) {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "debt_case");
        p.put("tableName", "mtcadm_debt_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("transitions", "escalate_by_hand>in_progress>>" + guard + ">");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actEscalate");
        p.put("actTable", "mtcadm_debt_case_act");
        p.put("checkRoles", "false");
        p.put("fromState", "open");
        p.put("refused", "audit>" + HISTORY);
        p.put("moveName", "Escalate this case now");
        if (requires != null) p.put("requires", requires);
        return p;
    }

    private MoveRefusal refusal() {
        return new MoveRefusal(new TransitionGuard(Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneId.of("UTC")),
                ANNA));
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        record = row("case-uuid", "case_number", "DC-2026-013260", "state", "open", "outstanding_amount", "1200.00");
        when(dao.find(eq("frmCase"), eq("mtcadm_debt_case"), eq("WHERE e.customProperties.case_number = ?1"),
                org.mockito.AdditionalMatchers.aryEq(new Object[]{"DC-2026-013260"}), isNull(), eq(Boolean.FALSE),
                eq(0), eq(2))).thenReturn(set(record));
    }

    private void finalDemands(long n) {
        when(dao.count(eq("recNotice"), eq("mtcadm_notice"), anyString(), any(Object[].class))).thenReturn(n);
    }

    private List<FormRowSet> history() {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeast(0)).saveOrUpdate(eq("case_history"), eq("mtcadm_history"), rows.capture());
        return rows.getAllValues();
    }

    private void theRecordIsNeverSaved() {
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
        verify(dao, never()).saveOrUpdate(eq("actEscalate"), anyString(), any(FormRowSet.class));
        assertEquals("open", record.getProperty("state"));
        assertEquals("1200.00", record.getProperty("outstanding_amount"));
    }

    @Test
    public void aRefusedRequirementWritesOneHistoryRowWithWhoAskedAndChangesNothing() {
        finalDemands(0);
        MoveRefusal.Judged j = refusal().judge(props("", LINE), dao, "anna", "DC-2026-013260", "");
        assertEquals(MESSAGE, j.words);
        assertTrue(j.byRequires);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(dao).count(eq("recNotice"), eq("mtcadm_notice"), eq(COUNTED), params.capture());
        assertArrayEquals(new Object[]{"DC-2026-013260"}, params.getValue());
        List<FormRowSet> rows = history();
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).size());
        FormRow h = rows.get(0).get(0);
        assertEquals("DC-2026-013260", h.getProperty("debt_case"));
        assertEquals("anna", h.getProperty("made_by"));                // who asked
        assertEquals("open", h.getProperty("value_before"));
        assertEquals("open", h.getProperty("value_after"));            // the case stays where it was
        assertEquals(MESSAGE, h.getProperty("words"));
        assertEquals("refused", h.getProperty("trigger_kind"));
        assertEquals("anna", h.getCreatedBy());                       // the row stamped as the platform stamps one
        theRecordIsNeverSaved();
    }

    @Test
    public void aRequirementMetIsLeftToTheGuardAsBefore() {
        finalDemands(1);
        assertNull(refusal().judge(props("", LINE), dao, "anna", "DC-2026-013260", ""));
        assertEquals(0, history().size());
    }

    @Test
    public void anAllowedMoveIsMadeByTheGuardAsBefore() {
        // MoveRefusal lets the move through; the guard, which reads no `requires`, makes it as before
        finalDemands(1);
        assertNull(refusal().judge(props("", LINE), dao, "anna", "DC-2026-013260", ""));
        MmConfigService cfg = mock(MmConfigService.class);
        when(cfg.entityScopeHasRules("debt_case", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("debt_case", "DEFAULT")).thenReturn(set(row("m1", "fromStatus", "open",
                "toStatus", "in_progress", "transitionCode", "escalate_by_hand", "roles", "officer")));
        Map<String, String> p = props("", LINE);
        p.put("recordId", "act-1");
        FormRow act = row("act-1", "lc_action", "escalate_by_hand", "record_key", "DC-2026-013260");
        when(dao.load("actEscalate", "mtcadm_debt_case_act", "act-1")).thenReturn(act);
        String result = new TransitionGuard(Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneId.of("UTC")), ANNA)
                .run(p, dao, cfg, new StatusManager(dao, cfg, mock(CaseEventWriter.class)), u -> Arrays.asList("officer"),
                        "anna");
        assertEquals("OK: in_progress", result);
        assertEquals("in_progress", record.getProperty("state"));
        assertEquals(0, history().size());
    }

    @Test
    public void aRequirementThatCannotBeCountedRefusesAndWritesTheRow() {
        when(dao.count(anyString(), anyString(), anyString(), any(Object[].class))).thenThrow(new RuntimeException("x"));
        MoveRefusal.Judged j = refusal().judge(props("", LINE), dao, "anna", "DC-2026-013260", "");
        assertEquals(MESSAGE, j.words);
        assertEquals(1, history().size());
        theRecordIsNeverSaved();
    }

    @Test
    public void aRequirementWithoutWordsIsSaidInTheRootGuardsOwn() {
        finalDemands(0);
        String line = LINE.substring(0, LINE.lastIndexOf('|') + 1);
        assertEquals(MoveRefusal.NOT_FOUND, refusal().refuse(props("", line), dao, "anna", "DC-2026-013260", ""));
        assertEquals("This action requires a related record that does not exist.", MoveRefusal.NOT_FOUND);
        assertEquals("This action requires a related record, and this one has no key to look it up by.",
                MoveRefusal.NO_KEY);
        assertEquals("This action requires a related record, and the check could not be run.", MoveRefusal.NOT_RUN);
    }

    @Test
    public void aRecordWithNoValueToLookUpByIsRefused() {
        record.setProperty("case_number", "");
        assertEquals(MESSAGE, MoveRefusal.requiresFails(LINE, "escalate_by_hand", record, dao));
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
    }

    @Test
    public void theRecordsOwnIdIsLookedUpWhereTheRequirementMatchesOnId() {
        finalDemands(0);
        String line = "lc_action|escalate_by_hand|recNotice|mtcadm_notice|debt_case|record_id||" + MESSAGE;
        assertEquals(MESSAGE, MoveRefusal.requiresFails(line, "escalate_by_hand", record, dao));
        verify(dao).count("recNotice", "mtcadm_notice", "WHERE e.customProperties.debt_case = ?1",
                new Object[]{"case-uuid"});
    }

    @Test
    public void aRequirementOfAnotherMoveOrNoneIsNotAsked() {
        finalDemands(0);
        assertNull(MoveRefusal.requiresFails(LINE, "conclude", record, dao));
        assertNull(MoveRefusal.requiresFails("", "escalate_by_hand", record, dao));
        assertNull(MoveRefusal.requiresFails("lc_action|escalate_by_hand|x", "escalate_by_hand", record, dao));
        // without the key, the validator judges the move exactly as before: nothing counted, nothing written
        assertNull(refusal().judge(props("", null), dao, "anna", "DC-2026-013260", ""));
        verify(dao, never()).count(anyString(), anyString(), anyString(), any(Object[].class));
        assertEquals(0, history().size());
    }

    @Test
    public void theRequirementIsJudgedBeforeTheGuardsTermsAndOneRowIsWritten() {
        finalDemands(0);
        MoveRefusal.Judged j = refusal().judge(props("outstanding_amount eq 0", LINE), dao, "anna", "DC-2026-013260", "");
        assertEquals(MESSAGE, j.words);
        assertEquals(1, history().size());
        // with the requirement met, the guard's failed term refuses as before, in the guard's words, not by the requirement
        setUp();
        finalDemands(1);
        j = refusal().judge(props("outstanding_amount eq 0", LINE), dao, "anna", "DC-2026-013260", "");
        assertFalse(j.byRequires);
        assertEquals("Escalate this case now is refused: outstanding amount must be 0, and it is 1200.00.", j.words);
        assertEquals(1, history().size());
    }

    @Test
    public void theWordsAreSaidOnceWhereTheRootGuardHasSaidThem() {
        assertTrue(MoveRefusal.saidAlready(MESSAGE, MESSAGE));
        assertTrue(MoveRefusal.saidAlready("Another rule's words." + MESSAGE, MESSAGE));
        assertFalse(MoveRefusal.saidAlready(null, MESSAGE));
        assertFalse(MoveRefusal.saidAlready("", MESSAGE));
        assertFalse(MoveRefusal.saidAlready("Something else.", MESSAGE));
        assertFalse(MoveRefusal.saidAlready(MESSAGE, ""));
    }
}
