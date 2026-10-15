package global.govstack.smartsearch.record;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search of the record the settings name. What was typed is first looked for exactly in the
 * exact column (a number identifies one record); where that finds nothing, it is looked for as a
 * fragment of the fragment column, case aside (a part of a name). Each row found carries the value
 * the element stores, the columns the result shows, and the values its choice fills into the form.
 * Where nothing is found, the result carries the settings' words, with what was searched for.
 *
 * <p>The statement is built from the settings' plain names only; what was typed is bound as a
 * parameter, and a wildcard in it is searched for as itself.
 */
public final class RecordSearch {

    /** A record found. */
    public static final class Row {
        public final String value;
        public final String label;
        public final List<String> cells;
        public final Map<String, String> fills;

        Row(String value, String label, List<String> cells, Map<String, String> fills) {
            this.value = value;
            this.label = label;
            this.cells = Collections.unmodifiableList(cells);
            this.fills = Collections.unmodifiableMap(fills);
        }
    }

    /** What a search found: the rows, whether there were more, how they were found, and, when
     *  nothing was found, the words to show. */
    public static final class Result {
        public final List<Row> rows;
        public final boolean more;
        public final String how;
        public final String message;

        Result(List<Row> rows, boolean more, String how, String message) {
            this.rows = Collections.unmodifiableList(rows);
            this.more = more;
            this.how = how;
            this.message = message;
        }

        /** The answer as the page reads it. */
        public String toJson() {
            List<Object> out = new ArrayList<>();
            for (Row r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("value", r.value);
                m.put("label", r.label);
                m.put("cells", r.cells);
                m.put("fills", r.fills);
                out.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rows", out);
            m.put("more", more);
            m.put("how", how);
            if (message != null) {
                m.put("message", message);
            }
            return Json.of(m);
        }
    }

    private final RecordSearchSettings s;
    private final DataSource ds;
    private final List<String> selected = new ArrayList<>();

    public RecordSearch(RecordSearchSettings settings, DataSource ds) {
        if (!settings.isRecordMode() || !settings.problems().isEmpty()) {
            throw new IllegalArgumentException("the settings cannot search: " + settings.problems());
        }
        this.s = settings;
        this.ds = ds;
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        cols.add(s.storeColumn());
        cols.add(s.labelColumn());
        for (RecordSearchSettings.Column c : s.columns()) {
            cols.add(c.column);
        }
        for (RecordSearchSettings.Fill f : s.fills()) {
            cols.add(f.column);
        }
        selected.addAll(cols);
    }

    /** Search for what was typed: exactly first, then as a fragment. */
    public Result search(String typed) throws SQLException {
        String term = typed == null ? "" : typed.trim();
        if (!term.isEmpty() && s.exactColumn() != null) {
            List<Row> rows = run(s.exactColumn() + " = ?", term);
            if (!rows.isEmpty()) {
                return result(rows, "exact");
            }
        }
        if (!term.isEmpty() && s.fragmentColumn() != null) {
            List<Row> rows = run("LOWER(" + s.fragmentColumn() + ") LIKE ? ESCAPE '!'", likePattern(term));
            if (!rows.isEmpty()) {
                return result(rows, "fragment");
            }
        }
        return new Result(new ArrayList<>(), false, "none", s.notFoundWords(term));
    }

    /** The statement for a condition: the columns read, the record, the order, one row more than
     *  is shown so that more can be said. */
    String sql(String condition) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String c : new String[]{s.fragmentColumn(), s.exactColumn(), s.storeColumn()}) {
            if (c != null) {
                order.add(c);
            }
        }
        return "SELECT " + String.join(", ", selected) + " FROM " + s.table() + " WHERE " + condition
                + " ORDER BY " + String.join(", ", order) + " LIMIT " + (RecordSearchSettings.MAX_ROWS + 1);
    }

    /** A fragment as a LIKE pattern, lower case, with '!' as the escape: a '%' or '_' typed is
     *  searched for as itself. */
    static String likePattern(String term) {
        String t = term.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + t + "%";
    }

    private List<Row> run(String condition, String param) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql(condition))) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> at = new LinkedHashMap<>();
                    for (int i = 0; i < selected.size(); i++) {
                        String v = rs.getString(i + 1);
                        at.put(selected.get(i), v == null ? "" : v);
                    }
                    List<String> cells = new ArrayList<>();
                    for (RecordSearchSettings.Column col : s.columns()) {
                        cells.add(at.get(col.column));
                    }
                    Map<String, String> fills = new LinkedHashMap<>();
                    for (RecordSearchSettings.Fill f : s.fills()) {
                        fills.put(f.field, at.get(f.column));
                    }
                    rows.add(new Row(at.get(s.storeColumn()), at.get(s.labelColumn()), cells, fills));
                }
            }
        }
        return rows;
    }

    private static Result result(List<Row> rows, String how) {
        boolean more = rows.size() > RecordSearchSettings.MAX_ROWS;
        List<Row> shown = more ? new ArrayList<>(rows.subList(0, RecordSearchSettings.MAX_ROWS)) : rows;
        return new Result(shown, more, how, null);
    }
}
