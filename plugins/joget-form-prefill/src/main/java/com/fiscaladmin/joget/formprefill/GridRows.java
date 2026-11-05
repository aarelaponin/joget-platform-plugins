package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The lines a grid carries from the record its form is opened on - the pure part of
 * {@link GridRowsPrefillBinder}, with no platform in it, so that it is unit-testable.
 *
 * <p>joget-spec-kit METHOD-2026-10-23-03 projects a request's grid with {@code rowsFrom} (the
 * plan for fixing the method, step S6, the second construct: "the request's lines carried from
 * the case"); METHOD-2026-10-24-01 carries it into the build. The configuration, written by the
 * library's gen_forms from the spec: the rows of {@code rowsTable} whose {@code parentField} holds
 * the key of the record the form is opened on (read from the address under {@code keyParam}) and
 * that meet {@code where} (an HQL predicate over {@code e.customProperties}) become one line
 * each, the line referring to its row by {@code refField} (the row's {@code refColumn}, its
 * {@code id} by default) and taking the values {@code map} names ({@code from} a field of the
 * row, {@code to} a field of the line). A line carries no {@code id}: it is a new row of the
 * grid's own table, given its key by the grid's store binder when the request is saved.</p>
 */
public final class GridRows {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");

    private GridRows() { }

    /** The configuration, read from the binder's properties. */
    public static final class Config {
        public final String rowsTable;
        public final String parentField;
        public final String keyParam;
        public final String refField;
        public final String refColumn;
        public final String where;
        public final List<String[]> map;

        Config(String rowsTable, String parentField, String keyParam, String refField,
               String refColumn, String where, List<String[]> map) {
            this.rowsTable = rowsTable;
            this.parentField = parentField;
            this.keyParam = keyParam;
            this.refField = refField;
            this.refColumn = refColumn == null || refColumn.isEmpty() ? "id" : refColumn;
            this.where = where == null ? "" : where.trim();
            this.map = map;
        }

        /** True when every name is a plain name and the four that must be there are there. */
        public boolean isUsable() {
            if (!plain(rowsTable) || !plain(parentField) || !plain(keyParam) || !plain(refField)
                    || !plain(refColumn)) {
                return false;
            }
            for (String[] m : map) {
                if (!plain(m[0]) || !plain(m[1])) {
                    return false;
                }
            }
            return !where.contains(";");
        }

        public static Config from(Function<String, Object> getter) {
            List<String[]> map = new ArrayList<>();
            for (Map<String, String> r : PrefillConfig.grid(getter.apply("map"))) {
                map.add(new String[]{str(r.get("from")), str(r.get("to"))});
            }
            return new Config(str(getter.apply("rowsTable")), str(getter.apply("parentField")),
                    str(getter.apply("keyParam")), str(getter.apply("refField")),
                    str(getter.apply("refColumn")), str(getter.apply("where")),
                    Collections.unmodifiableList(map));
        }
    }

    static boolean plain(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** The condition the rows are found by, for FormDataDao.find: one parameter, the key. */
    public static String condition(Config cfg) {
        String c = "WHERE e.customProperties." + cfg.parentField + " = ?";
        return cfg.where.isEmpty() ? c : c + " AND (" + cfg.where + ")";
    }

    /** One line for each row found, in the order found. */
    public static List<Map<String, String>> carry(Config cfg, List<Map<String, String>> rows) {
        List<Map<String, String>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, String> row : rows) {
            String ref = row.get(cfg.refColumn);
            if (ref == null || ref.isEmpty()) {
                continue;
            }
            Map<String, String> line = new LinkedHashMap<>();
            line.put(cfg.refField, ref);
            for (String[] m : cfg.map) {
                String v = row.get(m[0]);
                line.put(m[1], v == null ? "" : v);
            }
            out.add(line);
        }
        return out;
    }
}
