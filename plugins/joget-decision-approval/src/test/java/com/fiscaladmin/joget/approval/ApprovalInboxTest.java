package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/** Unit tests for {@link ApprovalInbox#eligible} — the per-user "mine to decide" filter. */
public class ApprovalInboxTest {

    private static Map<String, Object> row(String status, String requiredLevel, String requestedBy,
                                            String delegatedTo, String id) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("status", status);
        m.put("requiredLevel", requiredLevel);
        m.put("requestedBy", requestedBy);
        m.put("delegatedTo", delegatedTo);
        m.put("id", id);
        return m;
    }

    private static List<Map<String, Object>> rows(Map<String, Object>... rs) {
        List<Map<String, Object>> l = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : rs) l.add(r);
        return l;
    }

    @Test
    public void managerSeesRequestsAtOrBelowRank_notOwn_notOthersDelegated() {
        List<Map<String, Object>> in = rows(
                row("Pending", "OFFICER", "alice", "", "A"),   // eligible (manager outranks officer)
                row("Pending", "MANAGER", "bob", "", "B"),      // eligible (equal rank)
                row("Pending", "DIRECTOR", "carol", "", "C"),   // NOT: outranks manager
                row("Pending", "OFFICER", "dave", "", "D"),     // eligible
                row("Approved", "OFFICER", "eve", "", "E"),     // NOT: not pending
                row("Pending", "OFFICER", "manager1", "", "F"), // NOT: own request (four-eyes)
                row("Pending", "OFFICER", "x", "someoneElse", "G")); // NOT: delegated elsewhere

        List<Map<String, Object>> out = ApprovalInbox.eligible(in, "manager1", "MANAGER");
        List<Object> ids = new ArrayList<Object>();
        for (Map<String, Object> r : out) ids.add(r.get("id"));
        assertEquals(java.util.Arrays.asList("A", "B", "D"), ids);
    }

    @Test
    public void delegatedRequest_shownOnlyToDelegate() {
        List<Map<String, Object>> in = rows(row("Pending", "OFFICER", "alice", "bob", "A"));
        assertEquals(1, ApprovalInbox.eligible(in, "bob", "MANAGER").size());
        assertEquals(0, ApprovalInbox.eligible(in, "carol", "MANAGER").size());
    }

    @Test
    public void noResolvableAuthority_seesNothing() {
        List<Map<String, Object>> in = rows(row("Pending", "OFFICER", "alice", "", "A"));
        assertTrue(ApprovalInbox.eligible(in, "nobody", "").isEmpty());
    }

    @Test
    public void blankUser_seesNothing() {
        List<Map<String, Object>> in = rows(row("Pending", "OFFICER", "alice", "", "A"));
        assertTrue(ApprovalInbox.eligible(in, "  ", "MANAGER").isEmpty());
    }
}
