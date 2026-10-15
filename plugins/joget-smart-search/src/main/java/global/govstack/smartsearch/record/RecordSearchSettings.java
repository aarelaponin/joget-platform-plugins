package global.govstack.smartsearch.record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * What the smart search element searches when its properties name a record: the record (its table
 * or view), the column searched exactly (a number) and the column searched by a fragment (a name),
 * the columns the result shows, the fields of the form a choice fills and which of them are
 * locked, and the words shown when nothing is found. Every one of them is a property of the
 * element, written by whoever builds the form; none is written into the code, and no domain is
 * named here. An element that names no record ({@link #isRecordMode()} false) is the farmer search
 * as it always was, and these settings are not read.
 *
 * <p>Table and column names reach an SQL statement, so each must be a plain name
 * ({@link #problems()}); what a person types is only ever bound as a parameter.
 */
public final class RecordSearchSettings {

    public static final String RECORD_TABLE = "recordTable";
    public static final String EXACT_COLUMN = "exactColumn";
    public static final String FRAGMENT_COLUMN = "fragmentColumn";
    public static final String RESULT_COLUMNS = "resultColumns";
    public static final String POPULATE = "populate";
    public static final String LOCKS = "locks";
    public static final String NOT_FOUND = "notFound";
    public static final String STORE_VALUE = "storeValue";
    public static final String ENTITY_LABEL = "entityLabel";

    /** The most rows a result shows; a search that finds more says so. */
    public static final int MAX_ROWS = 20;
    /** Where, in the words when nothing is found, what was searched for is written. */
    public static final String SEARCHED = "{searched}";
    static final String DEFAULT_NOT_FOUND = "Nothing was found for " + SEARCHED + ".";
    static final String DEFAULT_ENTITY_LABEL = "record";

    private static final Pattern COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63})?");

    /** A column of the result, with the words at its head. */
    public static final class Column {
        public final String column;
        public final String label;

        Column(String column, String label) {
            this.column = column;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Column && ((Column) o).column.equals(column) && ((Column) o).label.equals(label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(column, label);
        }
    }

    /** A field of the form a choice fills, and the column of the record it is filled from. */
    public static final class Fill {
        public final String column;
        public final String field;

        Fill(String column, String field) {
            this.column = column;
            this.field = field;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Fill && ((Fill) o).column.equals(column) && ((Fill) o).field.equals(field);
        }

        @Override
        public int hashCode() {
            return Objects.hash(column, field);
        }
    }

    private final String table;
    private final String exact;
    private final String fragment;
    private final String store;
    private final String notFound;
    private final String entityLabel;
    private final List<Column> columns;
    private final List<Fill> fills;
    private final List<String> locks;
    private final List<String> problems;

    private RecordSearchSettings(String table, String exact, String fragment, String store, String notFound,
                                 String entityLabel, List<Column> columns, List<Fill> fills,
                                 List<String> locks, List<String> problems) {
        this.table = table;
        this.exact = exact;
        this.fragment = fragment;
        this.store = store;
        this.notFound = notFound;
        this.entityLabel = entityLabel;
        this.columns = Collections.unmodifiableList(columns);
        this.fills = Collections.unmodifiableList(fills);
        this.locks = Collections.unmodifiableList(locks);
        this.problems = Collections.unmodifiableList(problems);
    }

    /** The settings, read from the element's properties by name. */
    public static RecordSearchSettings from(Function<String, Object> property) {
        String table = text(property.apply(RECORD_TABLE));
        String exact = text(property.apply(EXACT_COLUMN));
        String fragment = text(property.apply(FRAGMENT_COLUMN));
        String store = text(property.apply(STORE_VALUE));
        String notFound = text(property.apply(NOT_FOUND));
        String entityLabel = text(property.apply(ENTITY_LABEL));
        List<String> problems = new ArrayList<>();
        List<Column> columns = columns(property.apply(RESULT_COLUMNS), problems);
        List<Fill> fills = new ArrayList<>();
        for (String pair : split(text(property.apply(POPULATE)))) {
            int at = pair.indexOf(':');
            String column = at < 0 ? pair : pair.substring(0, at).trim();
            String field = at < 0 ? "" : pair.substring(at + 1).trim();
            fills.add(new Fill(column, field));
        }
        List<String> locks = split(text(property.apply(LOCKS)));

        if (!table.isEmpty()) {
            if (!TABLE.matcher(table).matches()) {
                problems.add(RECORD_TABLE + " '" + table + "' is not the plain name of a table or a view");
            }
            if (exact.isEmpty() && fragment.isEmpty()) {
                problems.add("no column is searched: set " + EXACT_COLUMN + ", " + FRAGMENT_COLUMN + " or both");
            }
            plain(EXACT_COLUMN, exact, problems);
            plain(FRAGMENT_COLUMN, fragment, problems);
            plain(STORE_VALUE, store, problems);
            if (columns.isEmpty()) {
                problems.add(RESULT_COLUMNS + " names no column for the result to show");
            }
            for (Fill f : fills) {
                if (f.field.isEmpty() || !COLUMN.matcher(f.column).matches() || !COLUMN.matcher(f.field).matches()) {
                    problems.add(POPULATE + " entry '" + f.column + ":" + f.field
                            + "' is not <column of the record>:<field of the form>");
                }
            }
            for (String l : locks) {
                plain(LOCKS, l, problems);
            }
        }
        return new RecordSearchSettings(table, exact, fragment, store, notFound, entityLabel, columns, fills,
                locks, problems);
    }

    // ---- what the element and the search read --------------------------------------------

    /** Whether the element searches a record its properties name; otherwise it is the farmer search. */
    public boolean isRecordMode() {
        return !table.isEmpty();
    }

    /** Why the settings cannot search, one sentence each; empty when they can. */
    public List<String> problems() {
        return problems;
    }

    public String table() {
        return table;
    }

    /** The column searched exactly, or null. */
    public String exactColumn() {
        return exact.isEmpty() ? null : exact;
    }

    /** The column searched by a fragment, or null. */
    public String fragmentColumn() {
        return fragment.isEmpty() ? null : fragment;
    }

    /** The column whose value the element stores: its own setting, else the exact column. */
    public String storeColumn() {
        return !store.isEmpty() ? store : !exact.isEmpty() ? exact : fragment;
    }

    /** The column a chosen row is known by beside its value: the fragment column, else the exact. */
    public String labelColumn() {
        return !fragment.isEmpty() ? fragment : exact;
    }

    public String entityLabel() {
        return entityLabel.isEmpty() ? DEFAULT_ENTITY_LABEL : entityLabel;
    }

    public List<Column> columns() {
        return columns;
    }

    public List<String> columnLabels() {
        List<String> out = new ArrayList<>();
        for (Column c : columns) {
            out.add(c.label);
        }
        return out;
    }

    public List<Fill> fills() {
        return fills;
    }

    /** The fields of the form a choice fills, in order. */
    public List<String> fillFields() {
        List<String> out = new ArrayList<>();
        for (Fill f : fills) {
            if (!out.contains(f.field)) {
                out.add(f.field);
            }
        }
        return out;
    }

    /** The fields of the form that are locked: the person cannot type into them or change them. */
    public List<String> locks() {
        return locks;
    }

    /** The words shown when nothing is found, with what was searched for written in. */
    public String notFoundWords(String searched) {
        return (notFound.isEmpty() ? DEFAULT_NOT_FOUND : notFound).replace(SEARCHED, searched == null ? "" : searched);
    }

    // ---- the settings as the server seals them for the page, and reads them back ------------

    /** The settings as lines of text, in a fixed order, for the server to seal into the page. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        line(sb, RECORD_TABLE, table);
        line(sb, EXACT_COLUMN, exact);
        line(sb, FRAGMENT_COLUMN, fragment);
        line(sb, STORE_VALUE, store);
        line(sb, ENTITY_LABEL, entityLabel);
        line(sb, NOT_FOUND, notFound);
        StringBuilder pop = new StringBuilder();
        for (Fill f : fills) {
            pop.append(pop.length() > 0 ? "," : "").append(f.column).append(':').append(f.field);
        }
        line(sb, POPULATE, pop.toString());
        line(sb, LOCKS, String.join(",", locks));
        for (int i = 0; i < columns.size(); i++) {
            line(sb, RESULT_COLUMNS + "." + i + ".column", columns.get(i).column);
            line(sb, RESULT_COLUMNS + "." + i + ".label", columns.get(i).label);
        }
        return sb.toString();
    }

    /** The settings read back from {@link #toText()}. */
    public static RecordSearchSettings fromText(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String raw : (text == null ? "" : text).split("\n")) {
            int eq = raw.indexOf('=');
            if (eq > 0) {
                m.put(raw.substring(0, eq), unescape(raw.substring(eq + 1)));
            }
        }
        List<Object> cols = new ArrayList<>();
        for (int i = 0; m.containsKey(RESULT_COLUMNS + "." + i + ".column"); i++) {
            Map<String, String> c = new LinkedHashMap<>();
            c.put("column", m.get(RESULT_COLUMNS + "." + i + ".column"));
            c.put("label", m.getOrDefault(RESULT_COLUMNS + "." + i + ".label", ""));
            cols.add(c);
        }
        return from(name -> RESULT_COLUMNS.equals(name) ? cols : m.get(name));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecordSearchSettings)) {
            return false;
        }
        RecordSearchSettings s = (RecordSearchSettings) o;
        return table.equals(s.table) && exact.equals(s.exact) && fragment.equals(s.fragment)
                && store.equals(s.store) && notFound.equals(s.notFound) && entityLabel.equals(s.entityLabel)
                && columns.equals(s.columns) && fills.equals(s.fills) && locks.equals(s.locks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(table, exact, fragment, store, notFound, entityLabel, columns, fills, locks);
    }

    // ---- reading the properties ------------------------------------------------------------

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) {
            if (!p.trim().isEmpty()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    private static void plain(String property, String name, List<String> problems) {
        if (!name.isEmpty() && !COLUMN.matcher(name).matches()) {
            problems.add(property + " '" + name + "' is not a plain column name");
        }
    }

    /** The result's columns: the platform gives a grid property as rows of column and label; a
     *  plain "column:Label,column:Label" is read as well. */
    private static List<Column> columns(Object v, List<String> problems) {
        List<Object> rows = new ArrayList<>();
        if (v instanceof Object[]) {
            rows.addAll(Arrays.asList((Object[]) v));
        } else if (v instanceof Collection) {
            rows.addAll((Collection<?>) v);
        } else if (v != null) {
            for (String pair : split(String.valueOf(v))) {
                int at = pair.indexOf(':');
                Map<String, String> c = new LinkedHashMap<>();
                c.put("column", at < 0 ? pair : pair.substring(0, at).trim());
                c.put("label", at < 0 ? "" : pair.substring(at + 1).trim());
                rows.add(c);
            }
        }
        List<Column> out = new ArrayList<>();
        for (Object r : rows) {
            if (!(r instanceof Map)) {
                continue;
            }
            String column = text(((Map<?, ?>) r).get("column"));
            String label = text(((Map<?, ?>) r).get("label"));
            if (column.isEmpty()) {
                continue;
            }
            if (!COLUMN.matcher(column).matches()) {
                problems.add(RESULT_COLUMNS + " column '" + column + "' is not a plain column name");
                continue;
            }
            out.add(new Column(column, label.isEmpty() ? column : label));
        }
        return out;
    }

    private static void line(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r"))
          .append('\n');
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
