package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * Decision service: on a decision record the required authority level is resolved from the
 * authority matrix (actionType × amount band) and rank-compared against the approver's
 * level; collegial bodies additionally need a quorum of approvals. Reasoned grounds are
 * mandatory. Outcome + events (DECISION_PROPOSED / APPROVED / REJECTED) are written
 * idempotently. Constructor-injected for unit tests.
 */
public class DecisionService {

    public static final String F_DECISION = "cmDecision";
    public static final String F_AUTH = "mmAuthority";
    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Authority ranking (a controlled list; a deployment may externalise it). */
    private static final List<String> RANKS = Arrays.asList(
            "OFFICER", "SENIOR", "SUPERVISOR", "MANAGER", "DIRECTOR", "COMMISSIONER");

    private final FormDataDao dao;
    private final CaseEventWriter events;

    public DecisionService(FormDataDao dao, CaseEventWriter events) {
        this.dao = dao;
        this.events = events;
    }

    /** 1-based rank of a level name, or 0 when unknown/blank. */
    static int rank(String level) {
        int i = RANKS.indexOf(level == null ? "" : level.trim().toUpperCase());
        return i < 0 ? 0 : i + 1;
    }

    /** The authority level one step above {@code level}; the top level if already at/above it
     *  (used by approval escalation). An unknown level escalates to the lowest known. */
    static String nextRank(String level) {
        int i = RANKS.indexOf(level == null ? "" : level.trim().toUpperCase());
        if (i < 0) {
            return RANKS.get(0);
        }
        return i + 1 < RANKS.size() ? RANKS.get(i + 1) : RANKS.get(RANKS.size() - 1);
    }

    /** Authority-matrix row for (actionType, amount in band), or null. */
    FormRow requiredRow(String actionType, double amount) {
        FormRowSet rows = dao.find(F_AUTH, F_AUTH,
                "WHERE e.customProperties.actionType = ?1", new Object[]{actionType},
                "dateCreated", Boolean.FALSE, null, null);
        if (rows == null) {
            return null;
        }
        for (FormRow r : rows) {
            double min = Rows.parseDouble(Rows.prop(r, "amountMin"), 0);
            String maxStr = Rows.prop(r, "amountMax");
            double max = maxStr.isEmpty() ? Double.MAX_VALUE : Rows.parseDouble(maxStr, Double.MAX_VALUE);
            if (amount >= min && amount <= max) {
                return r;
            }
        }
        return null;
    }

    /** Process a decision record: resolve authority, set outcome, write events. */
    public String decide(String decisionId, String actor, LocalDateTime now) {
        dao.updateSchema(F_DECISION, F_DECISION, new FormRowSet());
        FormRow d = dao.load(F_DECISION, F_DECISION, decisionId);
        if (d == null) {
            return "decision not found: " + decisionId;
        }
        if (!Rows.prop(d, "result").isEmpty()) {
            return "no-op: already processed";
        }
        String caseId = caseId(d);
        String actionType = Rows.prop(d, "actionType");
        double amount = Rows.parseDouble(Rows.prop(d, "amount"), 0);
        String approverLevel = Rows.prop(d, "approverLevel");
        String bodyType = Rows.prop(d, "bodyType");
        int approvals = (int) Rows.parseLong(Rows.prop(d, "approvalsCount"), 0);
        String reasons = Rows.prop(d, "reasons");

        events.append(caseId, "DECISION_PROPOSED", actor, "", "",
                "decision proposed: " + actionType,
                "\"decisionId\":\"" + CaseEventWriter.esc(decisionId) + "\""
                        + ",\"actionType\":\"" + CaseEventWriter.esc(actionType) + "\""
                        + ",\"amount\":\"" + CaseEventWriter.esc(Rows.prop(d, "amount")) + "\"");

        String status;
        String result;
        if (reasons.isEmpty()) {
            status = "REJECTED";
            result = "REJECTED: reasoned grounds mandatory";
        } else {
            FormRow auth = requiredRow(actionType, amount);
            String requiredLevel = auth == null ? "OFFICER" : Rows.prop(auth, "level");
            int quorum = auth == null ? 0 : (int) Rows.parseLong(Rows.prop(auth, "quorum"), 0);
            boolean authorityOk = rank(approverLevel) >= rank(requiredLevel);
            boolean collegialOk = !"COLLEGIAL".equalsIgnoreCase(bodyType)
                    || approvals >= Math.max(quorum, 1);
            if (authorityOk && collegialOk) {
                status = "APPROVED";
                result = "APPROVED by " + approverLevel + " (required " + requiredLevel + ")";
            } else if (!authorityOk) {
                status = "REJECTED";
                result = "REJECTED: " + approverLevel + " below required " + requiredLevel;
            } else {
                status = "REJECTED";
                result = "REJECTED: collegial quorum " + quorum + " not met (" + approvals + ")";
            }
        }
        d.setProperty("decisionStatus", status);
        d.setProperty("result", result);
        d.setProperty("decidedAt", ISO.format(now));
        save(d);

        String eventType = "APPROVED".equals(status) ? "DECISION_APPROVED" : "DECISION_REJECTED";
        events.append(caseId, eventType, actor, "", "", result,
                "\"decisionId\":\"" + CaseEventWriter.esc(decisionId) + "\""
                        + ",\"actionType\":\"" + CaseEventWriter.esc(actionType) + "\"");
        return result;
    }

    /** Closure gate: an APPROVED decision exists for the case. */
    public boolean hasApprovedDecision(String caseId) {
        dao.updateSchema(F_DECISION, F_DECISION, new FormRowSet());
        Long n = dao.count(F_DECISION, F_DECISION,
                "WHERE e.customProperties.caseId = ?1 AND e.customProperties.decisionStatus = ?2",
                new Object[]{caseId, "APPROVED"});
        return n != null && n > 0;
    }

    private String caseId(FormRow d) {
        String c = Rows.prop(d, "caseId");
        return c.isEmpty() ? Rows.prop(d, "caseRef") : c;
    }

    private void save(FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(F_DECISION, F_DECISION, set);
    }
}
