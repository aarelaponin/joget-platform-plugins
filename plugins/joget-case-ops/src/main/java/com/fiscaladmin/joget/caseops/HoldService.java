package com.fiscaladmin.joget.caseops;

import java.time.LocalDateTime;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * Hold service. Asserts and releases the holds a case carries, keeping hold ↔
 * case-state consistency and appending {@code HOLD_ASSERTED} / {@code HOLD_RELEASED}
 * to the case event log. Suppression scopes and financial scopes are recorded with
 * their scope + owning target so downstream guards can consult {@link #hasActiveScope}.
 *
 * <p>Project-neutral: the hold and hold-release carrier table ids default to neutral
 * values and are configurable (settable statics or the full constructor).</p>
 */
public class HoldService {

    /** Neutral default table ids. */
    public static final String DEFAULT_HOLD_FORM = "hold";
    public static final String DEFAULT_RELEASE_FORM = "holdRelease";

    private static volatile String defaultHoldFormId = DEFAULT_HOLD_FORM;
    private static volatile String defaultReleaseFormId = DEFAULT_RELEASE_FORM;

    /** Point the service at a consumer's hold/release tables (call once at start-up). */
    public static void setFormIds(String holdFormId, String releaseFormId) {
        if (holdFormId != null && !holdFormId.trim().isEmpty()) defaultHoldFormId = holdFormId.trim();
        if (releaseFormId != null && !releaseFormId.trim().isEmpty()) defaultReleaseFormId = releaseFormId.trim();
    }

    private final FormDataDao dao;
    private final CaseEventWriter events;
    private final String fHold;
    private final String fRelease;

    public HoldService(FormDataDao dao, CaseEventWriter events) {
        this(dao, events, defaultHoldFormId, defaultReleaseFormId);
    }

    public HoldService(FormDataDao dao, CaseEventWriter events, String holdFormId, String releaseFormId) {
        this.dao = dao;
        this.events = events;
        this.fHold = holdFormId;
        this.fRelease = releaseFormId;
    }

    /** Activate the referenced hold. Idempotent: an ACTIVE/RELEASED hold is a no-op. */
    public String assertHold(String holdId, String actor, LocalDateTime now) {
        // reads/writes never auto-create tables; a missing relation poisons the JTA tx
        // — ensure the carrier exists before first touch.
        dao.updateSchema(fHold, fHold, new FormRowSet());
        FormRow hold = dao.load(fHold, fHold, holdId);
        if (hold == null) {
            return "hold not found: " + holdId;
        }
        String status = CaseOpsSupport.prop(hold, "status");
        if ("ACTIVE".equals(status) || "RELEASED".equals(status)) {
            return "no-op: hold already " + status;
        }
        String caseId = caseId(hold);
        String scope = CaseOpsSupport.prop(hold, "scope");
        hold.setProperty("status", "ACTIVE");
        hold.setProperty("assertedBy", actor == null ? "" : actor);
        hold.setProperty("assertedAt", CaseOpsSupport.ISO.format(now));
        hold.setProperty("result", "ASSERTED");
        save(fHold, hold);
        events.append(caseId, "HOLD_ASSERTED", actor, "", "",
                "hold asserted: " + scope,
                "\"holdId\":\"" + CaseEventWriter.esc(holdId) + "\""
                        + ",\"scope\":\"" + CaseEventWriter.esc(scope) + "\""
                        + ",\"holdType\":\"" + CaseEventWriter.esc(CaseOpsSupport.prop(hold, "holdType")) + "\""
                        + ",\"basis\":\"" + CaseEventWriter.esc(CaseOpsSupport.prop(hold, "basis")) + "\""
                        + ",\"targetBB\":\"" + CaseEventWriter.esc(CaseOpsSupport.prop(hold, "targetBB")) + "\"");
        return "ACTIVE " + scope;
    }

    /** Release the hold referenced by a release row. Idempotent. */
    public String release(String releaseId, String actor, LocalDateTime now) {
        dao.updateSchema(fHold, fHold, new FormRowSet());
        FormRow rel = dao.load(fRelease, fRelease, releaseId);
        if (rel == null) {
            return "release order not found: " + releaseId;
        }
        String holdId = CaseOpsSupport.prop(rel, "holdId");
        String reason = CaseOpsSupport.prop(rel, "releaseReason");
        String result;
        FormRow hold = holdId.isEmpty() ? null : dao.load(fHold, fHold, holdId);
        if (hold == null) {
            result = "hold not found: " + holdId;
        } else if ("RELEASED".equals(CaseOpsSupport.prop(hold, "status"))) {
            result = "no-op: hold already RELEASED";
        } else {
            String caseId = caseId(hold);
            String scope = CaseOpsSupport.prop(hold, "scope");
            hold.setProperty("status", "RELEASED");
            hold.setProperty("releasedBy", actor == null ? "" : actor);
            hold.setProperty("releasedAt", CaseOpsSupport.ISO.format(now));
            hold.setProperty("releaseReason", reason);
            save(fHold, hold);
            events.append(caseId, "HOLD_RELEASED", actor, "", "",
                    "hold released: " + scope,
                    "\"holdId\":\"" + CaseEventWriter.esc(holdId) + "\""
                            + ",\"reason\":\"" + CaseEventWriter.esc(reason) + "\"");
            result = "RELEASED " + scope;
        }
        rel.setProperty("result", result);
        save(fRelease, rel);
        return result;
    }

    /** Active holds of a scope for a case (read helper, e.g. for downstream guards). */
    public boolean hasActiveScope(String caseId, String scope) {
        Long n = dao.count(fHold, fHold,
                "WHERE e.customProperties.caseId = ?1 AND e.customProperties.scope = ?2"
                        + " AND e.customProperties.status = ?3",
                new Object[]{caseId, scope, "ACTIVE"});
        return n != null && n > 0;
    }

    private String caseId(FormRow hold) {
        String c = CaseOpsSupport.prop(hold, "caseId");
        return c.isEmpty() ? CaseOpsSupport.prop(hold, "caseRef") : c;
    }

    private void save(String form, FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, form, set);
    }
}
