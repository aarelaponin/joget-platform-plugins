package com.fiscaladmin.joget.transitionguard;

import java.util.Collection;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * Who may make a move. The lifecycle projector seeds one {@code mmEntityTransition} row per move
 * with the model's roles for it, comma-separated ({@code roles}); a person's roles are the
 * directory groups they belong to, whose ids are the model's role ids (the userview projector
 * grants every category to the same ids). Pure: no Joget service is touched here.
 */
final class MoveRoles {

    private MoveRoles() { }

    /**
     * The roles the state machine's row names for {@code action} taken from {@code from}; the
     * row for the action from any state when none matches the state exactly; {@code null} when
     * no row names the action at all (the state machine then refuses the move as illegal).
     */
    static String rolesFor(FormRowSet rows, String action, String from) {
        if (rows == null || action == null) {
            return null;
        }
        String anyState = null;
        for (FormRow r : rows) {
            if (!action.equals(r.getProperty("transitionCode"))) {
                continue;
            }
            String roles = nz(r.getProperty("roles"));
            if (from != null && from.equals(r.getProperty("fromStatus"))) {
                return roles;
            }
            if (anyState == null) {
                anyState = roles;
            }
        }
        return anyState;
    }

    /** True when the row names no role, or when the actor holds one of the roles it names. */
    static boolean allows(String rolesCsv, Collection<String> actorRoles) {
        if (rolesCsv == null || rolesCsv.trim().isEmpty()) {
            return true;
        }
        if (actorRoles == null || actorRoles.isEmpty()) {
            return false;
        }
        for (String role : rolesCsv.split(",")) {
            if (!role.trim().isEmpty() && actorRoles.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
