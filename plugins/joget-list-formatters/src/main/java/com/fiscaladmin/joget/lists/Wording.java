package com.fiscaladmin.joget.lists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules by which {@link WordedOptionsFormatter} words a stored value, apart from the platform: plain strings and
 * maps in, a string out, so that each rule is held by a case that needs no instance of Joget.
 *
 * <p>The platform's own formatter, {@code org.joget.plugin.enterprise.OptionsValueFormatter} (jw-enterprise-plugins
 * 9.0.7, javap -c), words a stored value as follows, and the rules below keep what it does except where they say they
 * do not:
 * <ul>
 * <li>{@code format}, offsets 0-6 and 118-122: a null value is answered as the empty string;</li>
 * <li>{@code format}, offsets 9-16: the value is cut with {@code String.split(";")};</li>
 * <li>{@code format}, offsets 56-104: a part that is a key of the option map is replaced by its words, any other part
 * is kept as it is;</li>
 * <li>{@code format}, offsets 111-118: the parts are joined with a comma and a space, {@code StringUtils.join(list, ", ")}:
 * constant #79 of the class file, a Utf8 two characters long, a comma and a space (the comment column of {@code javap -c}
 * shows no trailing space; the class file's bytes decide, R52).</li>
 * </ul>
 * What the platform does not do, and these rules do: {@code getOptionMap} offsets 23-243 fill the map from the grid
 * and then, whenever the binder answers (and {@code FormOptionsBinder.load} always answers), replace the map with the
 * binder's rows, so a grid beside a form binder is never drawn. {@link #merge} keeps the binder's rows and adds a grid
 * value only where no row holds it. The platform always splits; {@link #word} can leave the stored value whole.
 */
final class Wording {

    /** The character the platform cuts a stored value on (format, offset 14). */
    static final String SEPARATOR = ";";

    /** The string the platform joins the worded parts with: a comma and a space, two characters (format, offset 113; constant #79). */
    static final String JOINER = ", ";

    private Wording() {
    }

    /**
     * R1. The words of a value: the binder's rows, and then each grid value that no row of the binder holds. A record
     * that holds the value keeps its own words. A grid pair with no value or no words is left out, as the platform
     * leaves it out (getOptionMap, offsets 82-89).
     */
    static Map<String, String> merge(Map<String, String> binderRows, Map<String, String> grid) {
        Map<String, String> words = new LinkedHashMap<String, String>();
        if (binderRows != null) {
            words.putAll(binderRows);
        }
        if (grid != null) {
            for (Map.Entry<String, String> e : grid.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !words.containsKey(e.getKey())) {
                    words.put(e.getKey(), e.getValue());
                }
            }
        }
        return words;
    }

    /**
     * R2-R5. The stored value, worded.
     * <ul>
     * <li>R2: a null or empty value is empty;</li>
     * <li>R3: with {@code split} false the value is looked up whole, and a value of neither the binder nor the grid is
     * shown exactly as stored, {@code ;} and all;</li>
     * <li>R4: with {@code split} true the value is cut on {@code ;}, each part is worded or, where the words hold
     * nothing for it, kept as it is, and the parts are joined with a comma and a space, {@code ", "}, as the platform joins them;</li>
     * <li>R5: a value the words hold with no words (a null) is shown empty, as the join of the platform shows it.</li>
     * </ul>
     */
    static String word(Object stored, boolean split, Map<String, String> words) {
        if (stored == null) {
            return "";
        }
        String value = stored.toString();
        if (value.isEmpty()) {
            return "";
        }
        if (!split) {
            return lookup(value, words);
        }
        List<String> parts = new ArrayList<String>();
        for (String part : value.split(SEPARATOR)) {
            parts.add(lookup(part, words));
        }
        return String.join(JOINER, parts);
    }

    private static String lookup(String key, Map<String, String> words) {
        if (words != null && words.containsKey(key)) {
            String label = words.get(key);
            return label == null ? "" : label;
        }
        return key;
    }
}
