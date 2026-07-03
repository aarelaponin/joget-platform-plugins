package com.fiscaladmin.joget.formprefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for the pure prefill logic, driven by an in-memory {@link DataAccess}.
 * No Joget runtime — every branch the binder relies on is exercised here.
 */
public class PrefillResolverTest {

    // ---- fake data access --------------------------------------------------

    static final class FakeDA implements DataAccess {
        final Map<String, List<Map<String, String>>> finds = new HashMap<>();
        final Map<String, Map<String, String>> loads = new HashMap<>();
        final List<String> findCalls = new ArrayList<>();

        void onFind(String formId, String matchField, String key, List<Map<String, String>> rows) {
            finds.put(formId + "|" + matchField + "|" + key, rows);
        }
        void onLoad(String formId, String id, Map<String, String> row) {
            loads.put(formId + "|" + id, row);
        }
        @Override
        public List<Map<String, String>> findByField(String formId, String table, String matchField, String key) {
            findCalls.add(formId + "|" + matchField + "|" + key);
            return finds.get(formId + "|" + matchField + "|" + key);
        }
        @Override
        public Map<String, String> loadById(String formId, String table, String id) {
            return loads.get(formId + "|" + id);
        }
    }

    static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    // ---- config builders ---------------------------------------------------

    private static PrefillConfig cmCaseConfig(List<PrefillConfig.Filter> filters,
                                              String orderBy, boolean numeric,
                                              List<PrefillConfig.Related> related,
                                              List<PrefillConfig.Mapping> mappings,
                                              List<PrefillConfig.Constant> constants) {
        List<PrefillConfig.KeySource> keys = Arrays.asList(
                new PrefillConfig.KeySource("requestParam", "tin"));
        return new PrefillConfig(true, true, keys, "cmCase", "cmCase", "tin",
                filters, orderBy, numeric, true, related, mappings, constants);
    }

    private static List<PrefillConfig.Mapping> debtMappings() {
        return Arrays.asList(
                new PrefillConfig.Mapping("key", "tin"),
                new PrefillConfig.Mapping("id", "debtCaseId"),
                new PrefillConfig.Mapping("amountAtStake", "totalDebt"),
                new PrefillConfig.Mapping("category", "debtCategory"));
    }

    private static List<PrefillConfig.Filter> dmFilters() {
        return Arrays.asList(
                new PrefillConfig.Filter("caseType", "eq", "DM"),
                new PrefillConfig.Filter("currentState", "ne", "CLOSED"));
    }

    // ---- chooseKey ---------------------------------------------------------

    @Test
    public void chooseKey_firstNonEmptyWins() {
        assertEquals("100525M", PrefillResolver.chooseKey(Arrays.asList(null, "  ", "100525M", "X")));
        assertNull(PrefillResolver.chooseKey(Arrays.asList(null, "", "   ")));
        assertNull(PrefillResolver.chooseKey(null));
    }

    // ---- happy path + numeric pick-largest + eq/ne filters -----------------

    @Test
    public void resolve_picksLargestOpenCase_andMaps() {
        FakeDA da = new FakeDA();
        da.onFind("cmCase", "tin", "100525M", new ArrayList<>(Arrays.asList(
                row("id", "CASE_OLD", "tin", "100525M", "caseType", "DM", "currentState", "CLOSED", "amountAtStake", "99999", "category", "VAT"),
                row("id", "CASE_A", "tin", "100525M", "caseType", "DM", "currentState", "OPEN", "amountAtStake", "9112.42", "category", "VAT"),
                row("id", "CASE_B", "tin", "100525M", "caseType", "DM", "currentState", "ASSIGNED", "amountAtStake", "12000", "category", "CIT"))));

        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, null, debtMappings(), null);
        Map<String, String> out = PrefillResolver.resolve(cfg, Arrays.asList("", "100525M"), da);

        // CLOSED excluded by the ne filter; numeric order picks 12000 over 9112.42
        assertEquals("100525M", out.get("tin"));
        assertEquals("CASE_B", out.get("debtCaseId"));
        assertEquals("12000", out.get("totalDebt"));
        assertEquals("CIT", out.get("debtCategory"));
    }

    @Test
    public void resolve_lexicalOrderingDiffersFromNumeric() {
        FakeDA da = new FakeDA();
        da.onFind("cmCase", "tin", "T", new ArrayList<>(Arrays.asList(
                row("id", "A", "tin", "T", "caseType", "DM", "currentState", "OPEN", "amountAtStake", "9112.42", "category", "VAT"),
                row("id", "B", "tin", "T", "caseType", "DM", "currentState", "OPEN", "amountAtStake", "12000", "category", "CIT"))));
        // lexical: "9112.42" sorts after "12000" → picks A
        PrefillConfig lex = cmCaseConfig(dmFilters(), "amountAtStake", false, null, debtMappings(), null);
        assertEquals("A", PrefillResolver.resolve(lex, Arrays.asList("T"), da).get("debtCaseId"));
        // numeric: 12000 > 9112.42 → picks B
        PrefillConfig num = cmCaseConfig(dmFilters(), "amountAtStake", true, null, debtMappings(), null);
        assertEquals("B", PrefillResolver.resolve(num, Arrays.asList("T"), da).get("debtCaseId"));
    }

    // ---- related load + constants ------------------------------------------

    @Test
    public void resolve_mapsFromRelatedAlias_andConstants() {
        FakeDA da = new FakeDA();
        da.onFind("cmCase", "tin", "T", new ArrayList<>(Arrays.asList(
                row("id", "CASE_A", "tin", "T", "caseType", "DM", "currentState", "OPEN", "amountAtStake", "0", "category", "VAT"))));
        da.onLoad("dmDebt", "CASE_A", row("consolidatedAmount", "5000", "stage", "Active"));

        List<PrefillConfig.Related> related = Arrays.asList(
                new PrefillConfig.Related("dmDebt", "dmDebt", "id", "debt"));
        List<PrefillConfig.Mapping> mappings = Arrays.asList(
                new PrefillConfig.Mapping("key", "tin"),
                new PrefillConfig.Mapping("id", "caseId"),
                new PrefillConfig.Mapping("debt.consolidatedAmount", "totalDebt"));
        List<PrefillConfig.Constant> constants = Arrays.asList(
                new PrefillConfig.Constant("status", "DRAFT"));

        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, related, mappings, constants);
        Map<String, String> out = PrefillResolver.resolve(cfg, Arrays.asList("T"), da);

        assertEquals("T", out.get("tin"));
        assertEquals("CASE_A", out.get("caseId"));
        assertEquals("5000", out.get("totalDebt"));
        assertEquals("DRAFT", out.get("status"));
    }

    // ---- empty / guard paths -----------------------------------------------

    @Test
    public void resolve_noKey_returnsEmpty() {
        FakeDA da = new FakeDA();
        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, null, debtMappings(), null);
        assertTrue(PrefillResolver.resolve(cfg, Arrays.asList("", "  "), da).isEmpty());
        assertTrue("must not hit the dao without a key", da.findCalls.isEmpty());
    }

    @Test
    public void resolve_noMatch_returnsEmpty() {
        FakeDA da = new FakeDA();
        da.onFind("cmCase", "tin", "T", new ArrayList<>());
        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, null, debtMappings(), null);
        assertTrue(PrefillResolver.resolve(cfg, Arrays.asList("T"), da).isEmpty());
    }

    @Test
    public void resolve_allFilteredOut_returnsEmpty() {
        FakeDA da = new FakeDA();
        da.onFind("cmCase", "tin", "T", new ArrayList<>(Arrays.asList(
                row("id", "CASE_OLD", "tin", "T", "caseType", "DM", "currentState", "CLOSED", "amountAtStake", "5", "category", "VAT"))));
        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, null, debtMappings(), null);
        assertTrue(PrefillResolver.resolve(cfg, Arrays.asList("T"), da).isEmpty());
    }

    @Test
    public void resolve_unusableConfig_returnsEmpty() {
        FakeDA da = new FakeDA();
        // no mappings → not usable
        PrefillConfig cfg = cmCaseConfig(dmFilters(), "amountAtStake", true, null, new ArrayList<>(), null);
        assertTrue(PrefillResolver.resolve(cfg, Arrays.asList("T"), da).isEmpty());
    }

    @Test
    public void resolve_rejectsBadMatchFieldName() {
        FakeDA da = new FakeDA();
        List<PrefillConfig.KeySource> keys = Arrays.asList(new PrefillConfig.KeySource("requestParam", "tin"));
        PrefillConfig cfg = new PrefillConfig(true, true, keys, "cmCase", "cmCase",
                "tin; DROP", dmFilters(), "amountAtStake", true, true, null, debtMappings(), null);
        assertTrue(PrefillResolver.resolve(cfg, Arrays.asList("T"), da).isEmpty());
        assertTrue("must not query with an unsafe field name", da.findCalls.isEmpty());
    }
}
