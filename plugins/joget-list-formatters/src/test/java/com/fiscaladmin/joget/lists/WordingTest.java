package com.fiscaladmin.joget.lists;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The rules R1-R5 of {@link Wording}, each held by a case that needs no instance of Joget. */
public class WordingTest {

    private static Map<String, String> map(String... pairs) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    // R1 - the binder's rows, then the grid's values no row holds

    @Test
    public void aGridValueIsAddedWhereNoRowOfTheBinderHoldsIt() {
        Map<String, String> words = Wording.merge(map("u1", "Alice", "u2", "Bob"), map("system", "System"));
        assertEquals("Alice", words.get("u1"));
        assertEquals("System", words.get("system"));
        assertEquals(3, words.size());
    }

    @Test
    public void aRecordThatHoldsTheValueKeepsItsOwnWords() {
        Map<String, String> words = Wording.merge(map("system", "The system user"), map("system", "System"));
        assertEquals("The system user", words.get("system"));
    }

    @Test
    public void theBindersRowsComeFirstThenTheGridsValues() {
        Map<String, String> words = Wording.merge(map("b", "B"), map("a", "A"));
        assertEquals("[b, a]", words.keySet().toString());
    }

    @Test
    public void aGridPairWithNoValueOrNoWordsIsLeftOutAsThePlatformLeavesItOut() {
        Map<String, String> grid = new LinkedHashMap<String, String>();
        grid.put("x", null);
        grid.put(null, "Y");
        grid.put("z", "Z");
        Map<String, String> words = Wording.merge(null, grid);
        assertEquals("[z]", words.keySet().toString());
    }

    @Test
    public void noBinderAndNoGridMakeNoWords() {
        assertTrue(Wording.merge(null, null).isEmpty());
    }

    // R2 - empty

    @Test
    public void anEmptyOrNullValueIsShownEmpty() {
        Map<String, String> words = map("a", "A");
        assertEquals("", Wording.word(null, true, words));
        assertEquals("", Wording.word("", true, words));
        assertEquals("", Wording.word(null, false, words));
        assertEquals("", Wording.word("", false, words));
    }

    // R3 - whole

    @Test
    public void withSplitFalseTheValueIsLookedUpWhole() {
        Map<String, String> words = map("C1", "Cleared in error", "A", "Alpha");
        assertEquals("Cleared in error", Wording.word("C1", false, words));
        assertEquals("C1;A", Wording.word("C1;A", false, words));
    }

    @Test
    public void withSplitFalseAValueOfNeitherIsShownExactlyAsStoredSemicolonAndAll() {
        Map<String, String> words = map("C1", "Cleared in error");
        String own = "Cleared because a; b and c";
        assertEquals(own, Wording.word(own, false, words));
    }

    @Test
    public void withSplitFalseAWholeValueThatHoldsASemicolonCanHaveItsOwnWords() {
        Map<String, String> words = map("a;b", "Both");
        assertEquals("Both", Wording.word("a;b", false, words));
    }

    // R4 - split

    @Test
    public void withSplitTrueEachPartIsWordedAndTheyAreJoinedAsThePlatformJoinsThem() {
        Map<String, String> words = map("u1", "Alice", "system", "System");
        assertEquals("Alice, System, zzz", Wording.word("u1;system;zzz", true, words));
    }

    @Test
    public void withSplitTrueAPartOfNeitherIsKeptAsItIs() {
        assertEquals("x", Wording.word("x", true, map("a", "A")));
    }

    @Test
    public void theSplitIsTheStringsOwnSplitSoATrailingEmptyPartIsDroppedAsInThePlatform() {
        assertEquals("A", Wording.word("a;", true, map("a", "A")));
        assertEquals("", Wording.word(";", true, map("a", "A")));
        assertEquals("A, , A", Wording.word("a;;a", true, map("a", "A")));
    }

    @Test
    public void theJoinerIsACommaAndASpaceTwoCharactersAsConstant79OfThePlatformsFormatterHoldsIt() {
        assertEquals(2, Wording.JOINER.length());
        assertEquals(',', Wording.JOINER.charAt(0));
        assertEquals(' ', Wording.JOINER.charAt(1));
        assertEquals("Alice, Bob", Wording.word("u1;u2", true, map("u1", "Alice", "u2", "Bob")));
    }

    // R5 - a null word

    @Test
    public void aValueWhoseWordsAreNullIsShownEmptyAsThePlatformsJoinShowsIt() {
        Map<String, String> words = new LinkedHashMap<String, String>();
        words.put("a", null);
        assertEquals("", Wording.word("a", false, words));
        assertEquals("", Wording.word("a", true, words));
    }

    @Test
    public void withNoWordsAtAllEveryValueIsShownAsStored() {
        assertEquals("a;b", Wording.word("a;b", false, null));
        assertEquals("a, b", Wording.word("a;b", true, null));
    }
}
