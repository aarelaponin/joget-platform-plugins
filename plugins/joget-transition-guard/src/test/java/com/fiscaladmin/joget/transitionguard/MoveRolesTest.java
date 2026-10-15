package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
import java.util.Collections;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.*;

/** Who may make a move: the move's mmEntityTransition row against the actor's groups. */
public class MoveRolesTest {

    private static FormRow move(String code, String from, String roles) {
        FormRow r = new FormRow();
        r.setProperty("transitionCode", code);
        r.setProperty("fromStatus", from);
        r.setProperty("roles", roles);
        return r;
    }

    @Test
    public void theRowOfTheMoveFromTheRecordsStateNamesTheRoles() {
        FormRowSet rows = new FormRowSet();
        rows.add(move("reopen", "closed", "senior"));
        rows.add(move("reopen", "pending", "officer,senior"));
        rows.add(move("close", "open", "officer"));
        assertEquals("officer,senior", MoveRoles.rolesFor(rows, "reopen", "pending"));
        assertEquals("senior", MoveRoles.rolesFor(rows, "reopen", "closed"));
        assertEquals("senior", MoveRoles.rolesFor(rows, "reopen", "elsewhere"));   // any state
        assertNull(MoveRoles.rolesFor(rows, "fly", "open"));                     // no such move
    }

    @Test
    public void aRoleNamedIsHeldOrTheMoveIsRefused() {
        assertTrue(MoveRoles.allows("officer,senior", Arrays.asList("clerk", "senior")));
        assertTrue(MoveRoles.allows(" officer , senior ", Collections.singletonList("officer")));
        assertFalse(MoveRoles.allows("officer,senior", Collections.singletonList("clerk")));
        assertFalse(MoveRoles.allows("officer", Collections.<String>emptyList()));
        assertFalse(MoveRoles.allows("officer", null));
    }

    @Test
    public void aMoveWhoseRowNamesNoRoleIsNotGatedByRole() {
        assertTrue(MoveRoles.allows("", Collections.<String>emptyList()));
        assertTrue(MoveRoles.allows(null, null));
    }
}
