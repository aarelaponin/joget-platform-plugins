package com.fiscaladmin.joget.approval;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for the inversion seam: {@link DecisionEffects} static registry + the
 * {@link DecisionEffect} SPI. Proves a consumer-registered effect is what runs, and that
 * an unregistered action type fails loudly.
 */
public class DecisionEffectsTest {

    @After
    public void reset() {
        DecisionEffects.clear();
    }

    @Test
    public void registeredEffect_isReturnedAndRunnable() {
        AtomicReference<String> ran = new AtomicReference<String>();
        DecisionEffects.register("WRITE_OFF", (entity, recordId, actor, now) -> {
            ran.set(recordId + "/" + actor);
            return "written off " + recordId;
        });

        assertTrue(DecisionEffects.isRegistered("WRITE_OFF"));
        String result = DecisionEffects.get("WRITE_OFF").run("dmWriteOff", "W1", "alice", null);
        assertEquals("the consumer's effect body ran", "W1/alice", ran.get());
        assertEquals("written off W1", result);
    }

    @Test
    public void snapshot_isDetachedCopy() {
        DecisionEffects.register("A", (e, r, a, n) -> "a");
        assertEquals(1, DecisionEffects.snapshot().size());
        DecisionEffects.register("B", (e, r, a, n) -> "b");
        assertEquals("earlier snapshot is not mutated by later registration",
                2, DecisionEffects.snapshot().size());
    }

    @Test(expected = IllegalStateException.class)
    public void get_unknownActionType_throwsLoudly() {
        DecisionEffects.get("NOT_REGISTERED");
    }

    @Test(expected = IllegalArgumentException.class)
    public void register_blankActionType_rejected() {
        DecisionEffects.register("  ", (e, r, a, n) -> "x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void register_nullEffect_rejected() {
        DecisionEffects.register("X", null);
    }

    @Test
    public void lastRegistrationWins() {
        DecisionEffects.register("A", (e, r, a, n) -> { throw new RuntimeException("old"); });
        DecisionEffects.register("A", (e, r, a, n) -> "new");
        assertEquals("new", DecisionEffects.get("A").run("e", "r", "a", null));
    }
}
