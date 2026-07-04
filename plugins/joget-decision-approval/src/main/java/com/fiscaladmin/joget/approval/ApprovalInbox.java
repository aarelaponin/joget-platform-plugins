package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The pure eligibility rule behind the per-user "approvals mine to decide" inbox. Given the
 * Pending requests and the current user's directory-resolved rank level, it returns the subset
 * that THIS user may actually decide:
 * <ul>
 *   <li>status is Pending;</li>
 *   <li>the user's resolved rank is at least the request's current required level (the same rank
 *       gate the decide path enforces) — and a user with no resolvable authority sees nothing;</li>
 *   <li>the user is not the requester (four-eyes / separation of duties);</li>
 *   <li>if the request was delegated, it is shown only to the delegate.</li>
 * </ul>
 * Kept free of any Joget/runtime types so it is exhaustively unit-testable; a binder supplies the
 * live rows + resolved level.
 */
public final class ApprovalInbox {

    private ApprovalInbox() {
    }

    public static List<Map<String, Object>> eligible(List<Map<String, Object>> rows,
                                                     String username, String resolvedLevel) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (username == null || username.trim().isEmpty() || rows == null) {
            return out;
        }
        int mine = DecisionService.rank(resolvedLevel);
        if (mine <= 0) {
            return out; // no resolvable authority → empty inbox (safety default)
        }
        for (Map<String, Object> r : rows) {
            if (!"Pending".equalsIgnoreCase(str(r, "status"))) {
                continue;
            }
            if (mine < DecisionService.rank(str(r, "requiredLevel"))) {
                continue; // outranks the user
            }
            if (username.equalsIgnoreCase(str(r, "requestedBy"))) {
                continue; // four-eyes: cannot decide your own request
            }
            String delegatedTo = str(r, "delegatedTo");
            if (!delegatedTo.isEmpty() && !username.equalsIgnoreCase(delegatedTo)) {
                continue; // delegated to someone else
            }
            out.add(r);
        }
        return out;
    }

    static String str(Map<String, Object> r, String key) {
        Object v = r == null ? null : r.get(key);
        return v == null ? "" : v.toString();
    }
}
