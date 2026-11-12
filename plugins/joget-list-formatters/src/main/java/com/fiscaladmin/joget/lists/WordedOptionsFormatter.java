package com.fiscaladmin.joget.lists;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;

/**
 * A list column that words a stored value from an options binder's rows and a grid of further values (R47, version 2;
 * MT-DM's request of 9 November 2026).
 *
 * <p>The platform's {@code OptionsValueFormatter} (jw-enterprise-plugins 9.0.7) fills its map from its {@code options}
 * grid and then, whenever its options binder answers, replaces the map with the binder's rows (javap -c of
 * {@code getOptionMap}, offsets 23-243), and {@code FormOptionsBinder.load} always answers, so a value the binder's form
 * holds no row for, such as the solution's own {@code system} beside a list of users, is shown as stored. This
 * formatter keeps the binder's rows and adds the grid's values only where no row holds them (see {@link Wording}).
 *
 * <p>Its properties:
 * <ul>
 * <li>{@code optionsBinder}, an options binder chosen and configured as the platform's formatter takes one: a map of
 * {@code className} and {@code properties}, loaded as {@code getOptionMap} loads it (offsets 160-228);</li>
 * <li>{@code options}, a grid of further values, each with a {@code value} and a {@code label};</li>
 * <li>{@code split}, {@code true} (the default, as the platform's formatter) or {@code false}: with false the stored
 * value is looked up whole;</li>
 * <li>{@code unlisted}, where {@code as_stored} is the only value it takes: a value the words hold nothing for is shown
 * exactly as stored.</li>
 * </ul>
 * A binder that fails to load leaves the grid's words, and never stops the list.
 *
 * <p>How the platform finds and calls it (jw 9.0.7, javap -c): the list calls {@code DataListColumnFormat.format(list,
 * column, row, value)}; the plugin is found by class name through {@code PluginManager.getPlugin}, which asks the bundle
 * context for the service the {@link Activator} registered under that name and makes a new instance of it, so the words
 * are read once per use.
 */
public class WordedOptionsFormatter extends DataListColumnFormatDefault {

    /** The bundle's version; the pom's own, held by EveryClassReportsItsBundleVersionTest. */
    static final String VERSION = "1.0.0";

    private Map<String, String> words;

    @Override
    public String getName() {
        return "Worded Options Formatter";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Format a list column with the words of an options binder's rows and of a grid of further values";
    }

    @Override
    public String getLabel() {
        return "Worded Options Formatter";
    }

    @Override
    public String getClassName() {
        return WordedOptionsFormatter.class.getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/wordedOptionsFormatter.json", null, true, null);
    }

    @Override
    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        return Wording.word(value, split(), words());
    }

    /** {@code split} is true unless it says false: the platform's formatter always splits. */
    boolean split() {
        return !"false".equalsIgnoreCase(getPropertyString("split").trim());
    }

    /** The words, read once: the binder's rows, then the grid's values the binder holds no row for. */
    Map<String, String> words() {
        if (words == null) {
            words = Wording.merge(binderWords(), gridWords());
        }
        return words;
    }

    /** The grid's pairs, as the platform reads them (getOptionMap, offsets 23-111). */
    Map<String, String> gridWords() {
        Map<String, String> grid = new LinkedHashMap<String, String>();
        Object property = getProperty("options");
        Collection<?> entries = null;
        if (property instanceof Object[]) {
            entries = java.util.Arrays.asList((Object[]) property);
        } else if (property instanceof Collection) {
            entries = (Collection<?>) property;
        }
        if (entries == null) {
            return grid;
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Object value = ((Map<?, ?>) entry).get("value");
            Object label = ((Map<?, ?>) entry).get("label");
            if (value != null && label != null) {
                grid.put(value.toString(), label.toString());
            }
        }
        return grid;
    }

    /** The binder's rows, value to words; none where there is no binder or it fails to load. */
    Map<String, String> binderWords() {
        Map<String, String> rows = new LinkedHashMap<String, String>();
        FormRowSet set;
        try {
            set = loadBinderRows();
        } catch (Exception e) {
            LogUtil.error(WordedOptionsFormatter.class.getName(), e, "The options binder did not load; the grid's words stand");
            return rows;
        }
        if (set == null) {
            return rows;
        }
        for (FormRow row : set) {
            // As getOptionMap reads a row (offsets 275-365): the property "value", else the first property of the
            // row; the property "label", else the next one.
            Iterator<String> names = row.stringPropertyNames().iterator();
            String value = row.getProperty("value");
            if (value == null && names.hasNext()) {
                value = row.getProperty(names.next());
            }
            String label = row.getProperty("label");
            if (label == null && names.hasNext()) {
                label = row.getProperty(names.next());
            }
            if (value != null) {
                rows.put(value, label);
            }
        }
        return rows;
    }

    /**
     * The binder's rows, loaded as getOptionMap loads them (offsets 128-228): the binder is named by {@code className}
     * in the {@code optionsBinder} map, found by the plugin manager, given the map's {@code properties} and asked to
     * load with no element, no id and no form data. Null where no binder is named.
     */
    FormRowSet loadBinderRows() {
        Object binderProperty = getProperty("optionsBinder");
        if (!(binderProperty instanceof Map)) {
            return null;
        }
        Map<?, ?> binder = (Map<?, ?>) binderProperty;
        Object className = binder.get("className");
        if (className == null || className.toString().isEmpty()) {
            return null;
        }
        Object plugin = plugin(className.toString());
        if (!(plugin instanceof FormBinder) || !(plugin instanceof FormLoadBinder)) {
            return null;
        }
        Object properties = binder.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> binderProperties = properties instanceof Map ? (Map<String, Object>) properties : null;
        ((FormBinder) plugin).setProperties(binderProperties);
        return ((FormLoadBinder) plugin).load(null, null, null);
    }

    /** The plugin of a class name, from the platform's plugin manager (getOptionMap, offsets 160-186). */
    Object plugin(String className) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        return pluginManager.getPlugin(className);
    }
}
