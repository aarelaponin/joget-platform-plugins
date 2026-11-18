package com.fiscaladmin.joget.requireguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.joget.apps.form.lib.TextField;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * RequireGuard 1.1.0 refuses a value the move's page gives (joget-spec-kit METHOD-2026-11-05-95, R54; MT-DM's KR-4 of
 * 12 November 2026: an officer could conclude an enforcement action with the outcome Pending, since every place the kit
 * judged it read the record as stored).
 *
 * <p>A line of {@code rules} that begins {@code !refuse|} is a refusal,
 * {@code !refuse|whenField|whenEquals|field|refusedValues(csv)|message}: when the action is taken, the save is refused on
 * the field, in the message, where the value given for it is one of the refused values, letter case aside; an empty
 * value is the requirement rule's. Every line of 1.0.0 is read exactly as before. Each case below is run on a form of
 * the platform's own elements, by the guard's own {@code validate}, and reads the errors the platform's
 * {@link FormData} holds after it.
 */
public class RefusedValueTest {

    static final String WORDS_TODAY = "Give the date, the outcome and what came of it.";
    static final String TODAY = "lc_action|conclude|outcome,outcome_detail|" + WORDS_TODAY;
    static final String REFUSAL = "!refuse|lc_action|conclude|outcome|pending|Give the outcome.";

    private static Form form() {
        Form form = new Form();
        form.setProperty("id", "actEnforcemenConclude588");
        form.setProperty("tableName", "enf_action_act");
        Collection<Element> kids = new ArrayList<Element>();
        for (String id : new String[]{"lc_action", "outcome", "outcome_detail"}) {
            Element e = new TextField();
            e.setProperty("id", id);
            e.setParent(form);
            kids.add(e);
        }
        form.setChildren(kids);
        return form;
    }

    /** The errors after the guard, by field, with "_ok" holding what validate returned. */
    private static Map<String, String> run(String rules, String action, String outcome, String detail) {
        Form form = form();
        FormData data = new FormData();
        if (action != null) {
            data.addRequestParameterValues("lc_action", new String[]{action});
        }
        if (outcome != null) {
            data.addRequestParameterValues("outcome", new String[]{outcome});
        }
        if (detail != null) {
            data.addRequestParameterValues("outcome_detail", new String[]{detail});
        }
        RequireGuard guard = new RequireGuard();
        guard.setProperty("rules", rules);
        boolean ok = guard.validate(form, data, new String[0]);
        Map<String, String> out = new TreeMap<String, String>(data.getFormErrors());
        out.put("_ok", String.valueOf(ok));
        return out;
    }

    private static Map<String, String> errs(String... kv) {
        Map<String, String> m = new TreeMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ the refusal line

    @Test
    public void theRefusedValueIsRefusedOnItsFieldInTheLinesWords() {
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome."),
                run(TODAY + "\n" + REFUSAL, "conclude", "pending", "It was paid."));
    }

    @Test
    public void letterCaseAsideTheValueIsTheSame() {
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome."),
                run(TODAY + "\n" + REFUSAL, "conclude", "Pending", "x"));
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome."),
                run(TODAY + "\n" + REFUSAL, "conclude", "PENDING", "x"));
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome."),
                run(TODAY + "\n" + REFUSAL, "conclude", " pending ", "x"));
    }

    @Test
    public void anotherValueIsAdmitted() {
        assertEquals(errs("_ok", "true"), run(TODAY + "\n" + REFUSAL, "conclude", "unsuccessful", "x"));
        assertEquals(errs("_ok", "true"), run(TODAY + "\n" + REFUSAL, "conclude", "pending_review", "x"));
    }

    @Test
    public void anEmptyValueIsRefusedOnceInTheRequirementsWords() {
        assertEquals(errs("_ok", "false", "outcome", WORDS_TODAY), run(TODAY + "\n" + REFUSAL, "conclude", "", "x"));
        assertEquals(errs("_ok", "false", "outcome", WORDS_TODAY), run(TODAY + "\n" + REFUSAL, "conclude", null, "x"));
    }

    @Test
    public void anotherMoveIsNotJudgedByTheLine() {
        assertEquals(errs("_ok", "true"), run(REFUSAL, "begin", "pending", "x"));
        assertEquals(errs("_ok", "true"), run(REFUSAL, null, "pending", "x"));
    }

    @Test
    public void severalValuesStandOnOneLine() {
        String line = "!refuse|lc_action|conclude|outcome|pending, escalated|Give the outcome.";
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome."), run(line, "conclude", "escalated", "x"));
        assertEquals(errs("_ok", "true"), run(line, "conclude", "successful", "x"));
    }

    @Test
    public void aRefusalAndAnEmptyFieldAreEachRefusedOnTheirOwnField() {
        assertEquals(errs("_ok", "false", "outcome", "Give the outcome.", "outcome_detail", WORDS_TODAY),
                run(TODAY + "\n" + REFUSAL, "conclude", "pending", ""));
    }

    @Test
    public void aRefusalWithoutWordsSaysTheGuardsOwn() {
        assertEquals(errs("_ok", "false", "outcome", "This value is not accepted for the selected action."),
                run("!refuse|lc_action|conclude|outcome|pending|", "conclude", "pending", "x"));
    }

    @Test
    public void aMalformedRefusalIsSkippedAsAMalformedRuleIs() {
        assertEquals(errs("_ok", "true"), run("!refuse|lc_action|conclude|outcome", "conclude", "pending", "x"));
        assertEquals(errs("_ok", "true"), run("!refuse||conclude|outcome|pending|words", "conclude", "pending", "x"));
        assertEquals(errs("_ok", "true"), run("!refuse|lc_action|conclude||pending|words", "conclude", "pending", "x"));
        assertEquals(errs("_ok", "true"), run("!refuse|lc_action|conclude|outcome| , |words", "conclude", "pending", "x"));
        // a refusal with no condition field is skipped, even where its empty condition would match an empty value
        assertEquals(errs("_ok", "true"), run("!refuse|||outcome|pending|words", "conclude", "pending", "x"));
    }

    @Test
    public void theMessageIsTakenToTheEndOfTheLinePipesIncluded() {
        assertEquals(errs("_ok", "false", "outcome", "Give | the outcome."),
                run("!refuse|lc_action|conclude|outcome|pending|Give | the outcome.", "conclude", "pending", "x"));
    }

    // ------------------------------------------------------------------ every line of 1.0.0 read as before

    @Test
    public void aRuleOfTodayRefusesAnEmptyFieldAndAdmitsAValueWhateverItIs() {
        assertEquals(errs("_ok", "false", "outcome", WORDS_TODAY), run(TODAY, "conclude", "", "x"));
        assertEquals(errs("_ok", "true"), run(TODAY, "conclude", "pending", "x"));
        assertEquals(errs("_ok", "true"), run(TODAY, "begin", "", ""));
    }

    @Test
    public void aRuleOfTodayWithoutWordsSaysTheGuardsOwn() {
        assertEquals(errs("_ok", "false", "outcome", "This field is required for the selected action."),
                run("lc_action|conclude|outcome|", "conclude", "", "x"));
    }

    @Test
    public void aMalformedRuleOfTodayIsSkippedAsBefore() {
        assertEquals(errs("_ok", "true"), run("lc_action|conclude|outcome", "conclude", "", "x"));
        assertEquals(errs("_ok", "true"), run("|conclude|outcome|words", "conclude", "", "x"));
        assertEquals(errs("_ok", "true"), run("lc_action|conclude||words", "conclude", "", "x"));
    }

    @Test
    public void aRuleOfTodaysMessageKeepsItsPipes() {
        assertEquals(errs("_ok", "false", "outcome", "a|b"), run("lc_action|conclude|outcome|a|b", "conclude", "", "x"));
    }
}
