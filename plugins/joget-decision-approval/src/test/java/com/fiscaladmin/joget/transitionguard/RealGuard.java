package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import org.joget.apps.form.dao.FormDataDao;

/**
 * A test-only door to the real {@link TransitionGuard} for the cases of joget-decision-approval (METHOD-2026-11-05-79,
 * R46). The guard keeps its clock, its user and its collaborators behind package-private seams; the route engine's cases
 * need the real guard, not a stub, run with a clock and a user they pass in. This class sits in the guard's package, in the
 * decision-approval bundle's test tree only, and is never part of a jar.
 */
public final class RealGuard {

    private RealGuard() {
    }

    /**
     * Runs the real guard as the platform's plugin would, with {@code clock}, the platform's user ({@code user},
     * {@code name}), the groups the actor holds and the actor's name passed in. Returns what the guard returns.
     */
    public static String run(Clock clock, final String user, final String name, Map properties, FormDataDao dao,
                             MmConfigService cfg, StatusManager sm, final List<String> groups, String actor) {
        RowStamp.Who who = new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
        return new TransitionGuard(clock, who).run(properties, dao, cfg, sm, username -> groups, actor);
    }
}
