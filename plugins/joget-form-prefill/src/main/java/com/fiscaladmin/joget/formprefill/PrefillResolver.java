package com.fiscaladmin.joget.formprefill;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, runtime-agnostic prefill logic. Given a configuration, the ordered key
 * candidates the binder resolved, and a {@link DataAccess}, it returns the
 * {@code targetField -> value} map the binder writes onto the new form. Returns an
 * EMPTY map whenever there is nothing safe to prefill (no key, no match, etc.), so
 * the binder can fall straight back to the default load.
 *
 * <p>No Joget types here on purpose: every branch is unit-testable with a fake
 * {@link DataAccess}.</p>
 */
public final class PrefillResolver {

    private PrefillResolver() { }

    /** First non-blank, trimmed candidate, or {@code null}. */
    public static String chooseKey(List<String> candidates) {
        if (candidates == null) return null;
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) return c.trim();
        }
        return null;
    }

    public static Map<String, String> resolve(PrefillConfig cfg, List<String> keyCandidates, DataAccess da) {
        Map<String, String> out = new LinkedHashMap<>();
        if (cfg == null || !cfg.isUsable() || da == null) return out;

        String key = chooseKey(keyCandidates);
        if (key == null) return out;

        String matchField = sanitiseField(cfg.matchField);
        if (matchField == null) return out;

        List<Map<String, String>> rows = da.findByField(cfg.formId, cfg.table, matchField, key);
        if (rows == null || rows.isEmpty()) return out;

        // 1. apply post-find filters (eq / ne)
        List<Map<String, String>> kept = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (row != null && passesFilters(row, cfg.filters)) kept.add(row);
        }
        if (kept.isEmpty()) return out;

        // 2. order (numeric or lexical, descending) and pick the first
        sortDescending(kept, cfg.orderBy, cfg.orderNumeric);
        Map<String, String> primary = kept.get(0);

        // 3. related loads, exposed under their alias
        Map<String, Map<String, String>> aliases = new LinkedHashMap<>();
        if (cfg.related != null) {
            for (PrefillConfig.Related rel : cfg.related) {
                if (rel.alias == null || rel.alias.trim().isEmpty()) continue;
                String id = value(primary, rel.keyFrom);
                Map<String, String> loaded = (id == null || id.isEmpty())
                        ? null : da.loadById(rel.formId, rel.table, id);
                aliases.put(rel.alias.trim(), loaded);
            }
        }

        // 4. mappings (key | primaryField | alias.field) then constants
        for (PrefillConfig.Mapping m : cfg.mappings) {
            if (m.to == null || m.to.trim().isEmpty()) continue;
            out.put(m.to.trim(), nz(resolveSource(m.from, key, primary, aliases)));
        }
        if (cfg.constants != null) {
            for (PrefillConfig.Constant c : cfg.constants) {
                if (c.to != null && !c.to.trim().isEmpty()) out.put(c.to.trim(), nz(c.value));
            }
        }
        return out;
    }

    private static String resolveSource(String from, String key,
                                        Map<String, String> primary,
                                        Map<String, Map<String, String>> aliases) {
        if (from == null) return null;
        String f = from.trim();
        if (f.isEmpty()) return null;
        if ("key".equalsIgnoreCase(f)) return key;
        int dot = f.indexOf('.');
        if (dot > 0) {
            String alias = f.substring(0, dot);
            String field = f.substring(dot + 1);
            Map<String, String> rec = aliases.get(alias);
            return rec == null ? null : rec.get(field);
        }
        return value(primary, f);
    }

    private static boolean passesFilters(Map<String, String> row, List<PrefillConfig.Filter> filters) {
        if (filters == null) return true;
        for (PrefillConfig.Filter flt : filters) {
            String actual = nz(value(row, flt.field));
            String expected = nz(flt.value);
            boolean equal = actual.equalsIgnoreCase(expected);
            if ("ne".equalsIgnoreCase(flt.op)) {
                if (equal) return false;
            } else { // eq (default)
                if (!equal) return false;
            }
        }
        return true;
    }

    private static void sortDescending(List<Map<String, String>> rows, String orderBy, boolean numeric) {
        if (orderBy == null || orderBy.trim().isEmpty() || rows.size() < 2) return;
        final String field = orderBy.trim();
        Comparator<Map<String, String>> cmp;
        if (numeric) {
            cmp = Comparator.comparing(r -> toBigDecimal(value(r, field)));
        } else {
            cmp = Comparator.comparing(r -> nz(value(r, field)), String.CASE_INSENSITIVE_ORDER);
        }
        Collections.sort(rows, cmp.reversed());
    }

    private static BigDecimal toBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** Only word characters survive — keeps a config field name out of trouble. */
    static String sanitiseField(String field) {
        if (field == null) return null;
        String f = field.trim();
        return f.matches("[A-Za-z0-9_]+") ? f : null;
    }

    private static String value(Map<String, String> row, String field) {
        if (row == null || field == null) return null;
        return row.get(field.trim());
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
