package com.fiscaladmin.joget.approval;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link DecisionEffect}s keyed by action type — the seam that
 * inverts the effect coupling. The platform ships this empty; a consumer's Activator
 * calls {@link #register} at start-up to bind each action type to its domain effect. The
 * approval engines then obtain the effect via {@link #get}/{@link #snapshot} and never
 * name a domain service. Both bundles live in the same Joget runtime, so a static
 * registry is sufficient (promote to an OSGi service only when a second consumer needs
 * independent lifecycles).
 */
public final class DecisionEffects {

    private static final Map<String, DecisionEffect> REGISTRY = new ConcurrentHashMap<String, DecisionEffect>();

    private DecisionEffects() {
    }

    /** Bind an action type to its effect (last registration wins). */
    public static void register(String actionType, DecisionEffect effect) {
        if (actionType == null || actionType.trim().isEmpty()) {
            throw new IllegalArgumentException("actionType is required");
        }
        if (effect == null) {
            throw new IllegalArgumentException("effect is required for " + actionType);
        }
        REGISTRY.put(actionType.trim(), effect);
    }

    /** The effect for an action type. Throws if none is registered — a mis-seeded
     *  matrix fails loudly rather than silently skipping the effect. */
    public static DecisionEffect get(String actionType) {
        DecisionEffect e = actionType == null ? null : REGISTRY.get(actionType.trim());
        if (e == null) {
            throw new IllegalStateException("no DecisionEffect registered for action type: " + actionType);
        }
        return e;
    }

    public static boolean isRegistered(String actionType) {
        return actionType != null && REGISTRY.containsKey(actionType.trim());
    }

    /** A detached copy of the current registry (for constructing an approval service). */
    public static Map<String, DecisionEffect> snapshot() {
        return new HashMap<String, DecisionEffect>(REGISTRY);
    }

    /** Test/reset hook — clears all registrations. */
    public static void clear() {
        REGISTRY.clear();
    }
}
