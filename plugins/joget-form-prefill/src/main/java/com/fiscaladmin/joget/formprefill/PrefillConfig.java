package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable, runtime-agnostic view of a FormPrefillLoadBinder's configuration.
 *
 * <p>Parsed from the binder's plugin properties via {@link #from(Function)} — the
 * function is just {@code binder::getProperty}, so this class has NO Joget dependency
 * and is fully unit-testable. Grid properties arrive as an {@code Object[]} (or
 * {@code List}) of {@code Map} once Joget has parsed the form definition JSON.</p>
 */
public final class PrefillConfig {

    /** One ordered source the lookup key may come from. */
    public static final class KeySource {
        public final String source; // requestParam | loginUser | currentField | sessionAttr | workflowVar
        public final String name;
        public KeySource(String source, String name) { this.source = source; this.name = name; }
    }

    /** A post-find filter on the primary record set. */
    public static final class Filter {
        public final String field;
        public final String op;    // eq | ne  (default eq)
        public final String value;
        public Filter(String field, String op, String value) {
            this.field = field;
            this.op = (op == null || op.trim().isEmpty()) ? "eq" : op.trim();
            this.value = value;
        }
    }

    /** A related record to load by id, exposed under {@code alias} for the mapping. */
    public static final class Related {
        public final String formId;
        public final String table;   // defaults to formId
        public final String keyFrom; // a field on the already-resolved primary record
        public final String alias;
        public Related(String formId, String table, String keyFrom, String alias) {
            this.formId = formId;
            this.table = (table == null || table.trim().isEmpty()) ? formId : table.trim();
            this.keyFrom = keyFrom;
            this.alias = alias;
        }
    }

    /** Copy {@code from} (key | primaryField | alias.field) onto target field {@code to}. */
    public static final class Mapping {
        public final String from;
        public final String to;
        public Mapping(String from, String to) { this.from = from; this.to = to; }
    }

    /** A literal value written to target field {@code to}. */
    public static final class Constant {
        public final String to;
        public final String value;
        public Constant(String to, String value) { this.to = to; this.value = value; }
    }

    public final boolean enabled;
    public final boolean onlyOnAdd;
    public final List<KeySource> keySources;
    public final String formId;
    public final String table;
    public final String matchField;
    public final List<Filter> filters;
    public final String orderBy;
    public final boolean orderNumeric;
    public final boolean pickFirst;
    public final List<Related> related;
    public final List<Mapping> mappings;
    public final List<Constant> constants;

    public PrefillConfig(boolean enabled, boolean onlyOnAdd, List<KeySource> keySources,
                         String formId, String table, String matchField, List<Filter> filters,
                         String orderBy, boolean orderNumeric, boolean pickFirst,
                         List<Related> related, List<Mapping> mappings, List<Constant> constants) {
        this.enabled = enabled;
        this.onlyOnAdd = onlyOnAdd;
        this.keySources = keySources;
        this.formId = formId;
        this.table = (table == null || table.trim().isEmpty()) ? formId : table.trim();
        this.matchField = matchField;
        this.filters = filters;
        this.orderBy = orderBy;
        this.orderNumeric = orderNumeric;
        this.pickFirst = pickFirst;
        this.related = related;
        this.mappings = mappings;
        this.constants = constants;
    }

    /** True only when there is enough to attempt a lookup. */
    public boolean isUsable() {
        return enabled
                && formId != null && !formId.trim().isEmpty()
                && matchField != null && !matchField.trim().isEmpty()
                && keySources != null && !keySources.isEmpty()
                && mappings != null && !mappings.isEmpty();
    }

    // ---- parsing -----------------------------------------------------------

    public static PrefillConfig from(Function<String, Object> getter) {
        List<KeySource> keys = new ArrayList<>();
        for (Map<String, String> r : grid(getter.apply("keySources"))) {
            String s = str(r.get("source"));
            if (!s.isEmpty()) keys.add(new KeySource(s, str(r.get("name"))));
        }
        List<Filter> filters = new ArrayList<>();
        for (Map<String, String> r : grid(getter.apply("filters"))) {
            String f = str(r.get("field"));
            if (!f.isEmpty()) filters.add(new Filter(f, str(r.get("op")), str(r.get("value"))));
        }
        List<Related> related = new ArrayList<>();
        for (Map<String, String> r : grid(getter.apply("related"))) {
            String fid = str(r.get("formId"));
            if (!fid.isEmpty()) related.add(new Related(fid, str(r.get("table")), str(r.get("keyFrom")), str(r.get("alias"))));
        }
        List<Mapping> maps = new ArrayList<>();
        for (Map<String, String> r : grid(getter.apply("mappings"))) {
            String to = str(r.get("to"));
            if (!to.isEmpty()) maps.add(new Mapping(str(r.get("from")), to));
        }
        List<Constant> consts = new ArrayList<>();
        for (Map<String, String> r : grid(getter.apply("constants"))) {
            String to = str(r.get("to"));
            if (!to.isEmpty()) consts.add(new Constant(to, str(r.get("value"))));
        }
        return new PrefillConfig(
                boolStr(getter.apply("enabled"), true),
                boolStr(getter.apply("onlyOnAdd"), true),
                keys,
                str(getter.apply("formId")),
                str(getter.apply("table")),
                str(getter.apply("matchField")),
                filters,
                str(getter.apply("orderBy")),
                boolStr(getter.apply("orderNumeric"), false),
                boolStr(getter.apply("pickFirst"), true),
                related, maps, consts);
    }

    static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    static boolean boolStr(Object o, boolean dflt) {
        if (o == null) return dflt;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return dflt;
        return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(s);
    }

    /**
     * Normalise a Joget grid property value to a list of String maps. Joget hands grids
     * back as an {@code Object[]} (or {@code List}) of {@code Map} once the form
     * definition JSON is parsed; anything else yields an empty list (→ safe default load).
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, String>> grid(Object value) {
        List<Map<String, String>> out = new ArrayList<>();
        Iterable<?> rows = null;
        if (value instanceof Object[]) {
            List<Object> l = new ArrayList<>();
            for (Object o : (Object[]) value) l.add(o);
            rows = l;
        } else if (value instanceof Iterable) {
            rows = (Iterable<?>) value;
        }
        if (rows == null) return out;
        for (Object o : rows) {
            if (!(o instanceof Map)) continue;
            Map<String, String> r = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
                r.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
            out.add(r);
        }
        return out;
    }
}
