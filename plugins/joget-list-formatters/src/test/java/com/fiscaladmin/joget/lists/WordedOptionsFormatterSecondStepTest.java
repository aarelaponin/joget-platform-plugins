package com.fiscaladmin.joget.lists;

import java.util.HashMap;
import java.util.Map;

import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 1.1.0 (METHOD-2026-11-05-89, R51; MT-DM's K1 of 11 November 2026): the formatter's second step, against the platform's
 * own types. A column read through a reference whose attribute is bound to a list: the first binder, over the referred
 * record's form, gives the attribute's stored code for the referred record's key; the second, over the list's own table
 * (a maintained list), or the grid of the list's rows (a fixed list), gives the code's words. Both binders are the
 * platform's FormOptionsBinder, as the generator writes them, so the plugin table answers each by the form its
 * properties name.
 */
public class WordedOptionsFormatterSecondStepTest {

    private static final String BINDER = "org.joget.apps.form.lib.FormOptionsBinder";
    private static final String RECORDS = "recCaseScopeLine";
    private static final String LIST = "md_scope_line_exit";

    /** The formatter with its plugin manager replaced by a table of plugins: the rest is the formatter's own code. */
    static class Formatter extends WordedOptionsFormatter {
        final Map<String, Object> plugins = new HashMap<String, Object>();
        int asked;

        @Override
        Object plugin(String className) {
            asked++;
            return plugins.get(className);
        }
    }

    private static FormRow row(String value, String label) {
        FormRow r = new FormRow();
        r.setProperty("value", value);
        r.setProperty("label", label);
        return r;
    }

    private static FormRowSet rows(FormRow... rows) {
        FormRowSet set = new FormRowSet();
        for (FormRow r : rows) {
            set.add(r);
        }
        return set;
    }

    /** One FormOptionsBinder that answers by the form its properties name, as the platform's binder reads its form's table. */
    @SuppressWarnings("unchecked")
    private static FormBinder binderByForm(final Map<String, Object> byForm) {
        final FormBinder binder = mock(FormBinder.class, withSettings().extraInterfaces(FormLoadBinder.class));
        final Map<String, Object>[] last = new Map[1];
        doAnswer(inv -> {
            last[0] = (Map<String, Object>) inv.getArgument(0);
            return null;
        }).when(binder).setProperties(any());
        when(((FormLoadBinder) binder).load(any(), any(), any())).thenAnswer(inv -> {
            Object answer = byForm.get(String.valueOf(last[0] == null ? null : last[0].get("formDefId")));
            if (answer instanceof RuntimeException) {
                throw (RuntimeException) answer;
            }
            return answer;
        });
        return binder;
    }

    private static Map<String, Object> binderProperty(String formDefId, String idColumn, String labelColumn) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("formDefId", formDefId);
        properties.put("idColumn", idColumn);
        properties.put("labelColumn", labelColumn);
        Map<String, Object> property = new HashMap<String, Object>();
        property.put("className", BINDER);
        property.put("properties", properties);
        return property;
    }

    private static Map<String, Object> none() {
        Map<String, Object> property = new HashMap<String, Object>();
        property.put("className", "");
        property.put("properties", new HashMap<String, Object>());
        return property;
    }

    private static Map<String, Object> grid(String value, String label) {
        Map<String, Object> g = new HashMap<String, Object>();
        g.put("value", value);
        g.put("label", label);
        return g;
    }

    private static final FormRowSet LINES = rows(row("L1", "written_off"), row("L2", "paid"), row("L3", "moved"),
            row("L4", "reversed"));
    // a FormRow is a java.util.Properties and holds no null, so a code the table holds with no words is held at the rules'
    // level (WordingSecondStepTest.aNullCodeOrNullWordsAreShownEmpty)
    private static final FormRowSet TABLE = rows(row("written_off", "Written off"), row("paid", "Paid"));

    /** The column of Left the case as the generator writes it over a maintained list: two binders, no grid. */
    private Formatter overAMaintainedList(String split, Object listAnswer) {
        Map<String, Object> byForm = new HashMap<String, Object>();
        byForm.put(RECORDS, LINES);
        byForm.put(LIST, listAnswer);
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderByForm(byForm));
        f.setProperty("optionsBinder", binderProperty(RECORDS, "id", "how_it_left"));
        f.setProperty("options", new Object[0]);
        f.setProperty("split", split);
        f.setProperty("unlisted", "as_stored");
        f.setProperty("wordsBinder", binderProperty(LIST, "code", "name"));
        f.setProperty("words", new Object[0]);
        return f;
    }

    /** The same column over a fixed list, as the generator writes it: no second binder, the list's rows in the grid. */
    private Formatter overAFixedList(String split) {
        Map<String, Object> byForm = new HashMap<String, Object>();
        byForm.put(RECORDS, LINES);
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderByForm(byForm));
        f.setProperty("optionsBinder", binderProperty(RECORDS, "id", "how_it_left"));
        f.setProperty("options", new Object[0]);
        f.setProperty("split", split);
        f.setProperty("unlisted", "as_stored");
        f.setProperty("wordsBinder", none());
        f.setProperty("words", new Object[] { grid("written_off", "Written off"), grid("paid", "Paid") });
        return f;
    }

    private static String format(Formatter f, Object value) {
        return f.format(null, null, null, value);
    }

    @Test
    public void aCodeIsWordedByTheBinderOverAMaintainedListsTable() {
        Formatter f = overAMaintainedList("true", TABLE);
        assertEquals("Written off", format(f, "L1"));
        assertEquals("Paid", format(f, "L2"));
    }

    @Test
    public void theSecondBinderIsGivenItsOwnPropertiesAndAskedToLoadAsTheFirstIs() {
        Formatter f = overAMaintainedList("true", TABLE);
        format(f, "L1");
        FormBinder binder = (FormBinder) f.plugins.get(BINDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((Map<String, Object>) f.getProperty("optionsBinder")).get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) ((Map<String, Object>) f.getProperty("wordsBinder")).get("properties");
        verify(binder).setProperties(first);
        verify(binder).setProperties(second);
        verify((FormLoadBinder) binder, times(2)).load(null, null, null);
        assertEquals(2, f.asked);
    }

    @Test
    public void aCodeIsWordedByTheGridOfAFixedListsRows() {
        Formatter f = overAFixedList("true");
        assertEquals("Written off", format(f, "L1"));
        assertEquals("Paid", format(f, "L2"));
        assertEquals("one binder asked: the second names no class", 1, f.asked);
    }

    @Test
    public void aKeyTheFirstMapHoldsNothingForIsShownExactlyAsStoredAndTheSecondIsNotAsked() {
        Formatter f = overAFixedList("false");
        f.setProperty("words", new Object[] { grid("L9", "Never drawn"), grid("paid", "Paid") });
        assertEquals("L9", format(f, "L9"));
        assertEquals("a;b", format(f, "a;b"));
        Formatter g = overAMaintainedList("true", TABLE);
        assertEquals("L9", format(g, "L9"));
    }

    @Test
    public void aCodeTheSecondMapHoldsNothingForIsShownAsTheCode() {
        Formatter f = overAMaintainedList("true", TABLE);
        assertEquals("moved", format(f, "L3"));
        Formatter g = overAFixedList("true");
        assertEquals("moved", format(g, "L3"));
    }

    @Test
    public void aRowOfTheSecondBinderIsReadAsTheFirstsAndACodeWithNoWordsIsShownEmpty() {
        // as getOptionMap reads a row: the property "value", else the first; "label", else the next one - a row with one
        // property holds the code "reversed" with no words, which is shown empty, as the platform's join shows a null
        FormRow odd = new FormRow();
        odd.setProperty("code", "reversed");
        Formatter f = overAMaintainedList("true", rows(row("written_off", "Written off"), odd));
        assertEquals("Written off", format(f, "L1"));
        assertEquals("", format(f, "L4"));
    }

    @Test
    public void withSplitTrueEachPartTakesBothStepsAndThePartsAreJoinedWithACommaAndASpace() {
        Formatter f = overAMaintainedList("true", TABLE);
        assertEquals("Written off, Paid, L9, moved", format(f, "L1;L2;L9;L3"));
        Formatter g = overAFixedList("");
        assertTrue(g.split());
        assertEquals("Written off, Paid", format(g, "L1;L2"));
    }

    @Test
    public void withSplitFalseTheValueTakesBothStepsWhole() {
        Formatter f = overAMaintainedList("false", TABLE);
        assertEquals("Written off", format(f, "L1"));
        assertEquals("L1;L2", format(f, "L1;L2"));
    }

    @Test
    public void anEmptyOrNullValueIsShownEmpty() {
        for (String split : new String[] { "true", "false" }) {
            Formatter f = overAMaintainedList(split, TABLE);
            assertEquals("", format(f, null));
            assertEquals("", format(f, ""));
            Formatter g = overAFixedList(split);
            assertEquals("", format(g, null));
            assertEquals("", format(g, ""));
        }
    }

    @Test
    public void aSecondBinderThatFailsToLoadLeavesTheWordsGridAndNeverStopsTheList() {
        Formatter f = overAMaintainedList("true", new IllegalStateException("no database"));
        f.setProperty("words", new Object[] { grid("paid", "Paid") });
        assertEquals("Paid", format(f, "L2"));
        assertEquals("written_off", format(f, "L1"));
        assertEquals("L9", format(f, "L9"));
    }

    @Test
    public void aSecondBinderThatAnswersNothingLeavesTheWordsGrid() {
        Formatter f = overAMaintainedList("true", null);
        f.setProperty("words", new Object[] { grid("paid", "Paid") });
        assertEquals("Paid", format(f, "L2"));
        assertEquals("written_off", format(f, "L1"));
    }

    @Test
    public void aFirstBinderThatFailsToLoadLeavesTheFirstGridAndTheSecondStepStands() {
        Map<String, Object> byForm = new HashMap<String, Object>();
        byForm.put(RECORDS, new IllegalStateException("no database"));
        byForm.put(LIST, TABLE);
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderByForm(byForm));
        f.setProperty("optionsBinder", binderProperty(RECORDS, "id", "how_it_left"));
        f.setProperty("options", new Object[] { grid("L7", "paid") });
        f.setProperty("wordsBinder", binderProperty(LIST, "code", "name"));
        assertEquals("Paid", format(f, "L7"));
        assertEquals("L1", format(f, "L1"));
    }

    @Test
    public void theSecondBindersRowsComeBeforeTheWordsGrid() {
        Formatter f = overAMaintainedList("true", TABLE);
        f.setProperty("words", new Object[] { grid("paid", "Paid (grid)"), grid("moved", "Moved") });
        assertEquals("Paid", format(f, "L2"));
        assertEquals("Moved", format(f, "L3"));
    }

    @Test
    public void theSecondStepIsTakenWhereWordsBinderOrWordsIsSetAndNowhereElse() {
        Formatter none = new Formatter();
        assertFalse(none.secondStep());
        assertNull(none.codeWords());
        Formatter onlyGrid = new Formatter();
        onlyGrid.setProperty("words", new Object[] { grid("a", "A") });
        assertTrue(onlyGrid.secondStep());
        Formatter onlyBinder = new Formatter();
        onlyBinder.setProperty("wordsBinder", none());
        assertTrue(onlyBinder.secondStep());
        assertTrue(onlyBinder.codeWords().isEmpty());
        assertEquals("no binder is asked for a binder that names no class", 0, onlyBinder.asked);
    }

    @Test
    public void bothMapsAreReadOnceForAllTheRowsOfAList() {
        Formatter f = overAMaintainedList("true", TABLE);
        for (int i = 0; i < 5; i++) {
            format(f, "L1");
            format(f, "L2;L3");
        }
        verify((FormLoadBinder) f.plugins.get(BINDER), times(2)).load(any(), any(), any());
        assertEquals(2, f.asked);
    }
}
