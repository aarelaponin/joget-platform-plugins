package com.fiscaladmin.joget.lists;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 1.1.0 (METHOD-2026-11-05-89, R51): the rules R6-R8 of {@link Wording#word(Object, boolean, Map, Map)}, each held by a
 * case that needs no instance of Joget, and the rule that without a second map the value is worded as in one step.
 */
public class WordingSecondStepTest {

    private static Map<String, String> map(String... pairs) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static final Map<String, String> FIRST = map("L1", "written_off", "L2", "paid", "L3", "moved");
    private static final Map<String, String> SECOND = map("written_off", "Written off", "paid", "Paid");

    // R6 - a value the first map holds nothing for is shown exactly as stored

    @Test
    public void aValueTheFirstMapLacksIsShownExactlyAsStoredWholeOrSplit() {
        Map<String, String> second = map("L9", "Never drawn");
        assertEquals("L9", Wording.word("L9", false, FIRST, second));
        assertEquals("L9", Wording.word("L9", true, FIRST, second));
        assertEquals("a; b", Wording.word("a; b", false, FIRST, second));
    }

    // R7 - a code the second map holds nothing for is shown as the code

    @Test
    public void aCodeTheSecondMapLacksIsShownAsTheCode() {
        assertEquals("moved", Wording.word("L3", false, FIRST, SECOND));
        assertEquals("moved", Wording.word("L3", true, FIRST, SECOND));
    }

    @Test
    public void aCodeIsWordedByTheSecondMap() {
        assertEquals("Written off", Wording.word("L1", false, FIRST, SECOND));
        assertEquals("Paid", Wording.word("L2", true, FIRST, SECOND));
    }

    @Test
    public void aNullCodeOrNullWordsAreShownEmpty() {
        Map<String, String> first = new LinkedHashMap<String, String>();
        first.put("K", null);
        assertEquals("", Wording.word("K", false, first, SECOND));
        Map<String, String> second = new LinkedHashMap<String, String>();
        second.put("written_off", null);
        assertEquals("", Wording.word("L1", false, FIRST, second));
    }

    @Test
    public void anEmptySecondMapShowsTheCode() {
        assertEquals("written_off", Wording.word("L1", false, FIRST, map()));
    }

    // R8 - split: each part takes both steps, joined with a comma and a space

    @Test
    public void withSplitTrueEachPartTakesBothStepsAndThePartsAreJoinedAsThePlatformJoinsThem() {
        assertEquals("Written off, Paid, L9, moved", Wording.word("L1;L2;L9;L3", true, FIRST, SECOND));
        assertEquals("Written off", Wording.word("L1;", true, FIRST, SECOND));
        assertEquals("Written off, , Paid", Wording.word("L1;;L2", true, FIRST, SECOND));
    }

    @Test
    public void withSplitFalseTheWholeValueTakesBothSteps() {
        assertEquals("L1;L2", Wording.word("L1;L2", false, FIRST, SECOND));
        assertEquals("Paid", Wording.word("A;B", false, map("A;B", "paid"), SECOND));
    }

    @Test
    public void anEmptyOrNullValueIsShownEmpty() {
        assertEquals("", Wording.word(null, true, FIRST, SECOND));
        assertEquals("", Wording.word("", true, FIRST, SECOND));
        assertEquals("", Wording.word(null, false, FIRST, SECOND));
        assertEquals("", Wording.word("", false, FIRST, SECOND));
    }

    // without a second map, one step, as 1.0.0

    @Test
    public void withoutASecondMapTheValueIsWordedInOneStepExactlyAsBefore() {
        Map<String, String> words = map("u1", "Alice", "system", "System", "a;b", "Both");
        Object[] values = { null, "", "u1", "system", "zzz", "u1;system;zzz", "a;b", "a;", ";", "a;;a",
            "Cleared because a; b and c" };
        for (Object v : values) {
            for (boolean split : new boolean[] { true, false }) {
                assertEquals(String.valueOf(v) + " split " + split, Wording.word(v, split, words),
                        Wording.word(v, split, words, null));
            }
        }
        assertEquals("a, b", Wording.word("a;b", true, null, null));
    }
}
