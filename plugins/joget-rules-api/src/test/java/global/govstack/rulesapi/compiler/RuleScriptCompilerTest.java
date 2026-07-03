package global.govstack.rulesapi.compiler;

import global.govstack.rulesapi.model.ValidationResult;
import global.govstack.rulesapi.parser.RuleScriptParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end tests for the rules engine: Rules Script text -> parse (ANTLR, via
 * rules-grammar) -> compile to SQL against a FieldMapping. Exercises the whole
 * parse+compile path with no Joget runtime.
 */
public class RuleScriptCompilerTest {

    private FieldMapping mapping() {
        FieldMapping m = new FieldMapping();
        m.setMainTable("app_fd_person", "f");
        m.addField("age", "person", "f", "age", "NUMBER");
        m.addField("region", "person", "f", "region", "TEXT");
        return m;
    }

    private static final String ADULT =
        "RULE \"Adult\"\n" +
        "TYPE: INCLUSION\n" +
        "WHEN age >= 18\n";

    @Test
    public void emptyScriptIsValidWithNoRules() {
        ValidationResult r = new RuleScriptParser().parse("");
        assertTrue(r.isValid());
        assertEquals(0, r.getRuleCount());
    }

    @Test
    public void validScriptParsesToOneRule() {
        ValidationResult r = new RuleScriptParser().parse(ADULT);
        assertTrue("errors: " + r.getErrors(), r.isValid());
        assertEquals(1, r.getRuleCount());
        assertEquals(1, r.getRules().size());
    }

    @Test
    public void garbageScriptIsInvalidWithErrors() {
        ValidationResult r = new RuleScriptParser().parse("@@@ not a rule @@@");
        assertFalse(r.isValid());
        assertFalse(r.getErrors().isEmpty());
    }

    @Test
    public void compilesInclusionRuleToSqlReferencingMappedColumn() {
        ValidationResult r = new RuleScriptParser().parse(ADULT);
        assertTrue(r.isValid());

        CompiledRuleset cs = new RuleScriptCompiler(mapping()).compile(r.getRules(), "RS1", "eligibility");
        assertEquals("app_fd_person", cs.getMainTable());
        assertEquals(1, cs.getTotalRules());

        List<CompiledRuleset.CompiledRule> compiled = cs.getCompiledRules();
        assertEquals(1, compiled.size());
        String where = compiled.get(0).getWhereClause();
        assertNotNull(where);
        // age must be resolved through the mapping to its SQL column reference
        assertTrue("where clause was: " + where, where.contains("c_age"));
        assertTrue("where clause was: " + where, where.contains("18"));
    }
}
