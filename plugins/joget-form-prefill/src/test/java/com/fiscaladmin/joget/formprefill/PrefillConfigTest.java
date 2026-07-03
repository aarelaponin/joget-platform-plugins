package com.fiscaladmin.joget.formprefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

/**
 * Verifies that a binder's plugin properties (grids handed back as Object[] / List of
 * Map, text as String) parse into a usable {@link PrefillConfig}.
 */
public class PrefillConfigTest {

    static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void from_parsesGridsTextAndDefaults() {
        Map<String, Object> p = new HashMap<>();
        p.put("enabled", "true");
        p.put("keySources", new Object[]{ row("source", "requestParam", "name", "tin") });
        p.put("formId", "cmCase");
        p.put("matchField", "tin");
        // a List (not Object[]) — both shapes must work
        p.put("filters", Arrays.asList(
                row("field", "caseType", "op", "eq", "value", "DM"),
                row("field", "currentState", "op", "ne", "value", "CLOSED")));
        p.put("orderBy", "amountAtStake");
        p.put("orderNumeric", "true");
        p.put("related", new Object[]{ row("formId", "dmDebt", "keyFrom", "id", "alias", "debt") });
        p.put("mappings", new Object[]{
                row("from", "key", "to", "tin"),
                row("from", "amountAtStake", "to", "totalDebt") });
        p.put("constants", new Object[]{ row("to", "status", "value", "DRAFT") });
        // onlyOnAdd omitted → defaults true

        Function<String, Object> getter = p::get;
        PrefillConfig cfg = PrefillConfig.from(getter);

        assertTrue(cfg.enabled);
        assertTrue(cfg.onlyOnAdd);
        assertTrue(cfg.orderNumeric);
        assertTrue(cfg.pickFirst);
        assertEquals("cmCase", cfg.formId);
        assertEquals("cmCase", cfg.table); // defaults to formId
        assertEquals("tin", cfg.matchField);
        assertEquals("amountAtStake", cfg.orderBy);

        assertEquals(1, cfg.keySources.size());
        assertEquals("requestParam", cfg.keySources.get(0).source);
        assertEquals("tin", cfg.keySources.get(0).name);

        assertEquals(2, cfg.filters.size());
        assertEquals("eq", cfg.filters.get(0).op);
        assertEquals("ne", cfg.filters.get(1).op);

        assertEquals(1, cfg.related.size());
        assertEquals("dmDebt", cfg.related.get(0).table); // defaults to formId
        assertEquals("debt", cfg.related.get(0).alias);

        assertEquals(2, cfg.mappings.size());
        assertEquals(1, cfg.constants.size());
        assertTrue(cfg.isUsable());
    }

    @Test
    public void from_emptyProperties_notUsable() {
        Function<String, Object> getter = name -> null;
        PrefillConfig cfg = PrefillConfig.from(getter);
        assertFalse(cfg.isUsable());
        assertTrue(cfg.enabled);    // default
        assertTrue(cfg.keySources.isEmpty());
        assertTrue(cfg.mappings.isEmpty());
    }

    @Test
    public void grid_ignoresNonGridValues() {
        assertTrue(PrefillConfig.grid(null).isEmpty());
        assertTrue(PrefillConfig.grid("not a grid").isEmpty());
        assertTrue(PrefillConfig.grid(42).isEmpty());
    }
}
