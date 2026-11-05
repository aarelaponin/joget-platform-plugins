package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * A refusal's own words on the page (METHOD-2026-10-16-01, construct 1; MT-DM's request of 15
 * October 2026, item 1).
 *
 * <p>The guard refuses after the act is saved, as the form's post-processor, and the platform then
 * shows "Form Submitted": a post-processor is given no form data to write an error on. MoveRefusal
 * is a form validator on the move's hidden field, run before the act is stored. It judges the move
 * as the guard does, term by term; where a term the model words fails, it answers those words,
 * with {@code {attribute}} filled from the record moved or, for a term over another record's rows,
 * from the first row that breaks it, and writes the history entries and alerts the model marks for
 * a refusal (the guard's {@code refused} lines), since the post-processor will not run. A term
 * without words, or a record not in the state the move leaves from, is left to the guard and the
 * root guard as before. FormDataDao is mocked.
 */
public class MoveRefusalTest {

    static final String NONE_TERM = "none(action where dcase = this and kind = visit)";
    static final String OWE = "Cannot close: {case_number} still owes {amount}";
    static final String VISIT = "Cannot close: the visit {action_reference} is planned for {planned_for}";
    static final String HISTORY = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "made_by=actor,value_before=from,trigger_kind='refused'";

    private FormDataDao dao;
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

    /** The validator's properties, as the kit projects them for the move `close`. */
    private static Map<String, String> props() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("transitions", "close>closing>>amount eq 0 and " + NONE_TERM + ">");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("records", "action>frmAction>patmin_action");
        p.put("thisKey", "case_number");
        p.put("fromState", "open");
        p.put("refused", "audit>" + HISTORY);
        p.put("refusals", "amount eq 0|" + OWE + "\n" + NONE_TERM + "|" + VISIT);
        return p;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "250.00");
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), org.mockito.AdditionalMatchers.aryEq(new Object[]{"DC-000001"}),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(dao.count(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class)))
                .thenReturn(0L);
    }

    private FormRow history() {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq("case_history"), eq("patmin_history"), rows.capture());
        List<FormRowSet> all = rows.getAllValues();
        assertEquals(1, all.size());
        return all.get(0).get(0);
    }

    @Test
    public void aFailedTermOfTheRecordIsRefusedInItsOwnWordsFilledFromTheRecord() {
        String words = new MoveRefusal().refuse(props(), dao, "anna", "DC-000001", "");
        assertEquals("Cannot close: DC-000001 still owes 250.00", words);
        // the history entry the model marks for a refusal is written, as the guard would have
        FormRow h = history();
        assertEquals("DC-000001", h.getProperty("dcase"));
        assertEquals("anna", h.getProperty("made_by"));
        assertEquals("open", h.getProperty("value_before"));
        assertEquals("refused", h.getProperty("trigger_kind"));
        // and the record is never saved: nothing moved
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aFailedTermOverAnotherRecordIsRefusedInItsWordsFilledFromTheRowThatBreaksIt() {
        record.setProperty("amount", "0");
        when(dao.count(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class)))
                .thenReturn(1L);
        when(dao.find(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class),
                isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(set(row("a1", "action_reference", "EA-000007", "planned_for", "2026-10-20",
                        "kind", "visit", "dcase", "DC-000001")));
        String words = new MoveRefusal().refuse(props(), dao, "anna", "DC-000001", "");
        assertEquals("Cannot close: the visit EA-000007 is planned for 2026-10-20", words);
    }

    @Test
    public void aMoveWhoseTermsHoldIsNotRefused() {
        record.setProperty("amount", "0");
        assertNull(new MoveRefusal().refuse(props(), dao, "anna", "DC-000001", ""));
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
    }

    // ---- 1.7.0 (METHOD-2026-11-03-13; MT-DM's hand-off of 22 October 2026, items 1 and 3): no
    // refused move ends on "Form Submitted". Until 1.7.0 a failed term the model gave no words was
    // left to the guard after the save (refuse() returned null, and the platform said "Form
    // Submitted"); now it is refused before the save in words drawn from the guard.

    @Test
    public void aFailedTermTheModelGivesNoWordsIsRefusedBeforeTheSaveInTheGuardsWords() {
        Map<String, String> p = props();
        p.put("refusals", NONE_TERM + "|" + VISIT);          // no words for `amount eq 0`
        p.put("moveName", "Close the case");
        String words = new MoveRefusal().refuse(p, dao, "anna", "DC-000001", "");
        assertNotNull("a refused move is never left to the save behind 'Form Submitted'", words);
        assertEquals("Close the case is refused: amount must be 0, and it is 250.00.", words);
        // the history entry marked for a refusal is written before the save, as with model words
        assertEquals("refused", history().getProperty("trigger_kind"));
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aFailedTermOverAnotherRecordWithoutWordsIsRefusedWithTheCountTheGuardFound() {
        Map<String, String> p = props();
        p.remove("refusals");                                // the model words nothing
        record.setProperty("amount", "0");
        when(dao.count(eq("frmAction"), eq("patmin_action"), anyString(), any(Object[].class)))
                .thenReturn(2L);
        String words = new MoveRefusal().refuse(p, dao, "anna", "DC-000001", "");
        assertNotNull(words);
        assertEquals("Close is refused: it is made only while there is no action where dcase is "
                + "this record and kind = visit, and 2 are found.", words);
    }

    @Test
    public void theModelsOwnWordsStayFirstWhereTheModelGivesThem() {
        Map<String, String> p = props();
        p.put("moveName", "Close the case");
        assertEquals("Cannot close: DC-000001 still owes 250.00",
                new MoveRefusal().refuse(p, dao, "anna", "DC-000001", ""));
    }

    @Test
    public void theTermTheFormsRootGuardAlreadyAsksIsLeftToIt() {
        Map<String, String> p = props();
        p.remove("refusals");
        p.put("rootAsks", "amount eq 0");                    // the root guard says it in the model's words
        assertNull(new MoveRefusal().refuse(p, dao, "anna", "DC-000001", ""));
    }

    @Test
    public void aMoveWithheldFromItsRequesterWithoutWordsIsRefusedInTheGuardsWords() {
        Map<String, String> p = props();
        p.remove("refusals");
        p.put("withheld", "close>requested_by");
        p.put("moveName", "Close the case");
        record.setProperty("requested_by", "anna");
        String words = new MoveRefusal().refuse(p, dao, "anna", "DC-000001", "");
        assertEquals("Close the case is refused: you are the record's requested by, and another "
                + "person makes this move.", words);
    }

    @Test
    public void aRefusalsAlertWithoutATemplateRecordCarriesTheWordsNeverTheTemplatesId() {
        // the kit maps a refusal's alert column to `words` where no record provides its template
        Map<String, String> p = props();
        p.remove("refusals");
        p.put("moveName", "Close the case");
        p.put("refused", "notify>close>attr:holder>closure_refused>alert>patmin_alert>"
                + "addressed_to=recipient,summary=words,about=attr:case_number");
        record.setProperty("holder", "carmen.abela");
        String words = new MoveRefusal().refuse(p, dao, "anna", "DC-000001", "");
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao).saveOrUpdate(eq("alert"), eq("patmin_alert"), rows.capture());
        FormRow a = rows.getValue().get(0);
        assertEquals(words, a.getProperty("summary"));
        assertNotEquals("closure_refused", a.getProperty("summary"));
        assertTrue(a.getProperty("summary"), a.getProperty("summary").contains("Close the case is refused"));
        assertEquals("carmen.abela", a.getProperty("addressed_to"));
    }

    @Test
    public void aRecordNotInTheStateTheMoveLeavesFromIsLeftToTheRootGuard() {
        record.setProperty("state", "closing");
        assertNull(new MoveRefusal().refuse(props(), dao, "anna", "DC-000001", ""));
    }

    @Test
    public void noRecordNamedIsLeftToTheRootGuard() {
        assertNull(new MoveRefusal().refuse(props(), dao, "anna", "", ""));
        assertNull(new MoveRefusal().refuse(props(), dao, "anna", "DC-999999", ""));
    }

    @Test
    public void theWordsAreReadOnePerLineTheTermBeforeTheFirstBar() {
        Map<String, String> w = MoveRefusal.refusals("amount eq 0|Owes: a | b\n\n bad line \n" + NONE_TERM + "|V");
        assertEquals("Owes: a | b", w.get("amount eq 0"));
        assertEquals("V", w.get(NONE_TERM));
        assertEquals(2, w.size());
        assertEquals("x  y", MoveRefusal.fill("x {missing} y", row("r")));
    }
}
