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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The formatter against the platform's own types (FormBinder, FormLoadBinder, FormRow, FormRowSet, the
 * DataListColumnFormatDefault it extends): the binder is the platform's abstract class with its load interface, the
 * rows are FormRows, and the properties are set as the platform sets them ({@code setProperty}).
 */
public class WordedOptionsFormatterTest {

    private static final String BINDER = "org.joget.apps.form.lib.FormOptionsBinder";

    /** The formatter with its plugin manager replaced by a table of plugins: the rest is the formatter's own code. */
    static class Formatter extends WordedOptionsFormatter {
        final Map<String, Object> plugins = new HashMap<String, Object>();
        final java.util.List<String> asked = new java.util.ArrayList<String>();

        @Override
        Object plugin(String className) {
            asked.add(className);
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

    private static FormBinder binderAnswering(FormRowSet set) {
        FormBinder binder = mock(FormBinder.class, withSettings().extraInterfaces(FormLoadBinder.class));
        when(((FormLoadBinder) binder).load(any(), any(), any())).thenReturn(set);
        return binder;
    }

    private static Map<String, Object> grid(String value, String label) {
        Map<String, Object> g = new HashMap<String, Object>();
        g.put("value", value);
        g.put("label", label);
        return g;
    }

    private static Map<String, Object> binderProperty(String formDefId) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("formDefId", formDefId);
        properties.put("idColumn", "id");
        properties.put("labelColumn", "name");
        Map<String, Object> property = new HashMap<String, Object>();
        property.put("className", BINDER);
        property.put("properties", properties);
        return property;
    }

    /** A formatter over a binder of users and the solution's own value, as the column of made_by would carry it. */
    private Formatter madeBy(String split) {
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderAnswering(rows(row("u1", "Alice"), row("u2", "Bob"))));
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        f.setProperty("split", split);
        f.setProperty("unlisted", "as_stored");
        return f;
    }

    private static String format(Formatter f, Object value) {
        return f.format(null, null, null, value);
    }

    @Test
    public void aValueTheBindersFormHoldsNoRowForIsWordedFromTheGrid() {
        Formatter f = madeBy("false");
        assertEquals("Alice", format(f, "u1"));
        assertEquals("System", format(f, "system"));
    }

    @Test
    public void aRecordThatHoldsTheValueKeepsItsOwnWordsOverTheGrid() {
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderAnswering(rows(row("system", "The system user"))));
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        assertEquals("The system user", format(f, "system"));
    }

    @Test
    public void aValueOfNeitherTheBinderNorTheGridIsShownExactlyAsStored() {
        Formatter f = madeBy("false");
        assertEquals("someone-else", format(f, "someone-else"));
        assertEquals("a;b", format(f, "a;b"));
    }

    @Test
    public void withSplitFalseAPersonsOwnWordsWithASemicolonAreShownAsStored() {
        Formatter f = madeBy("false");
        assertEquals("Cleared because a; b and c", format(f, "Cleared because a; b and c"));
    }

    @Test
    public void withSplitTrueTheValueIsCutOnSemicolonWordedAndJoinedWithACommaAndASpace() {
        Formatter f = madeBy("true");
        assertEquals("Alice, System, zzz", format(f, "u1;system;zzz"));
    }

    @Test
    public void splitIsTrueWhereItIsNotSaidAndFalseOnlyWhereItSaysFalse() {
        Formatter f = madeBy("");
        assertTrue(f.split());
        assertEquals("Alice, Bob", format(f, "u1;u2"));
        f.setProperty("split", "FALSE");
        assertFalse(f.split());
        Formatter none = new Formatter();
        assertTrue(none.split());
    }

    @Test
    public void anEmptyOrNullValueIsShownEmpty() {
        Formatter f = madeBy("true");
        assertEquals("", format(f, null));
        assertEquals("", format(f, ""));
        Formatter g = madeBy("false");
        assertEquals("", format(g, null));
        assertEquals("", format(g, ""));
    }

    @Test
    public void theBinderIsGivenItsPropertiesAndAskedToLoadWithNoElementNoIdAndNoFormData() {
        Formatter f = madeBy("false");
        format(f, "u1");
        FormBinder binder = (FormBinder) f.plugins.get(BINDER);
        @SuppressWarnings("unchecked")
        Map<String, Object> given = (Map<String, Object>) ((Map<String, Object>) f.getProperty("optionsBinder")).get("properties");
        verify(binder).setProperties(given);
        verify((FormLoadBinder) binder).load(isNull(), isNull(), isNull());
        assertEquals(java.util.Collections.singletonList(BINDER), f.asked);
    }

    @Test
    public void theWordsAreReadOnceForAllTheRowsOfAList() {
        Formatter f = madeBy("false");
        for (int i = 0; i < 5; i++) {
            format(f, "u1");
            format(f, "system");
        }
        verify((FormLoadBinder) f.plugins.get(BINDER), times(1)).load(any(), any(), any());
        assertEquals(1, f.asked.size());
    }

    @Test
    public void aBinderThatFailsToLoadLeavesTheGridsWordsAndNeverStopsTheList() {
        Formatter f = new Formatter();
        FormBinder binder = mock(FormBinder.class, withSettings().extraInterfaces(FormLoadBinder.class));
        when(((FormLoadBinder) binder).load(any(), any(), any())).thenThrow(new IllegalStateException("no database"));
        f.plugins.put(BINDER, binder);
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        f.setProperty("split", "false");
        assertEquals("System", format(f, "system"));
        assertEquals("u1", format(f, "u1"));
    }

    @Test
    public void aPluginManagerThatFailsLeavesTheGridsWords() {
        Formatter f = new Formatter() {
            @Override
            Object plugin(String className) {
                throw new IllegalStateException("no application context");
            }
        };
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        assertEquals("System", format(f, "system"));
    }

    @Test
    public void aBinderThatAnswersNothingLeavesTheGridsWords() {
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderAnswering(null));
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        assertEquals("System", format(f, "system"));
    }

    @Test
    public void aPluginThatIsNotABinderLeavesTheGridsWords() {
        Formatter f = new Formatter();
        f.plugins.put(BINDER, "not a binder");
        f.setProperty("optionsBinder", binderProperty("users"));
        f.setProperty("options", new Object[] { grid("system", "System") });
        assertEquals("System", format(f, "system"));
    }

    @Test
    public void noBinderNamedMeansNoBinderIsAsked() {
        Formatter f = new Formatter();
        Map<String, Object> none = new HashMap<String, Object>();
        none.put("className", "");
        none.put("properties", new HashMap<String, Object>());
        f.setProperty("optionsBinder", none);
        f.setProperty("options", new Object[] { grid("a", "A") });
        assertEquals("A", format(f, "a"));
        assertTrue(f.asked.isEmpty());
        Formatter g = new Formatter();
        g.setProperty("options", new Object[] { grid("a", "A") });
        assertEquals("A", format(g, "a"));
        assertTrue(g.asked.isEmpty());
    }

    @Test
    public void aFormatterWithNeitherBinderNorGridShowsEveryValueAsStored() {
        Formatter f = new Formatter();
        f.setProperty("split", "false");
        assertEquals("a;b", format(f, "a;b"));
    }

    @Test
    public void aGridPairWithoutWordsIsLeftOutAsThePlatformLeavesItOut() {
        Formatter f = new Formatter();
        Map<String, Object> noLabel = new HashMap<String, Object>();
        noLabel.put("value", "x");
        f.setProperty("options", new Object[] { noLabel, grid("y", "Y") });
        f.setProperty("split", "false");
        assertEquals("x", format(f, "x"));
        assertEquals("Y", format(f, "y"));
    }

    @Test
    public void aRowWithOnlyOnePropertyIsReadAsThePlatformReadsItTheFirstPropertyIsTheValueAndTheWordsAreNone() {
        // getOptionMap, offsets 287-355: the property "value", else the row's first property; the property "label",
        // else the next one, and where there is none the words stay null, which the platform's join shows empty.
        FormRow odd = new FormRow();
        odd.setProperty("code", "c1");
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderAnswering(rows(odd)));
        f.setProperty("optionsBinder", binderProperty("x"));
        f.setProperty("split", "false");
        assertEquals("", format(f, "c1"));
    }

    @Test
    public void aRowWithValueAndLabelKeysIsReadByThoseKeys() {
        FormRow r = new FormRow();
        r.setProperty("label", "Alice");
        r.setProperty("value", "u1");
        r.setProperty("other", "ignored");
        Formatter f = new Formatter();
        f.plugins.put(BINDER, binderAnswering(rows(r)));
        f.setProperty("optionsBinder", binderProperty("x"));
        f.setProperty("split", "false");
        assertEquals("Alice", format(f, "u1"));
    }

    @Test
    public void theFormatterIsAColumnFormatAndNamesItselfAndItsBundlesVersion() {
        WordedOptionsFormatter f = new WordedOptionsFormatter();
        assertTrue(f instanceof org.joget.apps.datalist.model.DataListColumnFormat);
        assertEquals("com.fiscaladmin.joget.lists.WordedOptionsFormatter", f.getClassName());
        assertEquals("1.1.0", f.getVersion());   // moved for R51 (METHOD-2026-11-05-89): the bundle's version is 1.1.0
        assertEquals("Worded Options Formatter", f.getLabel());
    }
}
