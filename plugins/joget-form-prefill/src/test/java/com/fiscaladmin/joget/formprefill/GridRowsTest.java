package com.fiscaladmin.joget.formprefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** GridRows, the pure part of GridRowsPrefillBinder (METHOD-2026-10-24-01). */
public class GridRowsTest {

    private static Map<String, Object> props(String where) {
        Map<String, Object> p = new HashMap<>();
        p.put("rowsTable", "mtcadm_case_scope_line");
        p.put("parentField", "debt_case");
        p.put("keyParam", "debt_case");
        p.put("refField", "case_scope_line");
        p.put("refColumn", "id");
        p.put("where", where);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("from", "tax_type");
        m.put("to", "tax_type");
        p.put("map", new Object[]{m});
        return p;
    }

    private static Map<String, String> row(String id, String taxType) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("debt_case", "DC-2026-007417");
        r.put("tax_type", taxType);
        return r;
    }

    @Test
    public void eachRowOfTheRecordIsOneLineReferringToItsRow() {
        GridRows.Config cfg = GridRows.Config.from(props("")::get);
        assertTrue(cfg.isUsable());
        List<Map<String, String>> lines = GridRows.carry(cfg,
                Arrays.asList(row("b2e4adfe-1", "VAT"), row("b2e4adfe-2", "INCOME")));
        assertEquals(2, lines.size());
        assertEquals("b2e4adfe-1", lines.get(0).get("case_scope_line"));
        assertEquals("VAT", lines.get(0).get("tax_type"));
        assertEquals("INCOME", lines.get(1).get("tax_type"));
        assertFalse("a carried line is a new row of the grid's own table, with no id",
                lines.get(0).containsKey("id"));
    }

    @Test
    public void theConditionReadsTheParentByTheKeyAndTheWhere() {
        assertEquals("WHERE e.customProperties.debt_case = ?",
                GridRows.condition(GridRows.Config.from(props("")::get)));
        assertEquals("WHERE e.customProperties.debt_case = ? AND (e.customProperties.how_it_left is null)",
                GridRows.condition(GridRows.Config.from(props("e.customProperties.how_it_left is null")::get)));
    }

    @Test
    public void aNameThatIsNotPlainOrAWhereWithASemicolonIsNotUsed() {
        Map<String, Object> p = props("");
        p.put("parentField", "debt_case = 1 or 1");
        assertFalse(GridRows.Config.from(p::get).isUsable());
        assertFalse(GridRows.Config.from(props("1=1; drop table x")::get).isUsable());
        Map<String, Object> q = props("");
        q.remove("keyParam");
        assertFalse(GridRows.Config.from(q::get).isUsable());
    }

    @Test
    public void aRowWithoutTheColumnItIsReferredByIsNotALine() {
        GridRows.Config cfg = GridRows.Config.from(props("")::get);
        Map<String, String> r = row(null, "VAT");
        r.remove("id");
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(r);
        assertEquals(0, GridRows.carry(cfg, rows).size());
        assertEquals(0, GridRows.carry(cfg, null).size());
    }
}
