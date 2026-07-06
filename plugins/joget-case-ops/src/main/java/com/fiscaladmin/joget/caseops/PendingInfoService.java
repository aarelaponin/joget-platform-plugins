package com.fiscaladmin.joget.caseops;

import java.util.UUID;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;

/**
 * Pending-information loop. {@code request} parks the case in its OnHold-envelope
 * state (when the type has one), opens a PROVIDE_INFO task and queues a notice;
 * {@code respond} restores the pre-hold state and closes the task — the case is
 * never re-initiated. Any SLA pause-on-hold is handled elsewhere; no clock logic here.
 * All transitions are audited to the case event chain.
 *
 * <p>Project-neutral: the request/response/case/task carrier-table ids default to
 * neutral values and are configurable (settable statics or the full constructor). The
 * OnHold envelope lookup is delegated to the platform {@link MmConfigService}.</p>
 */
public class PendingInfoService {

    public static final String DEFAULT_REQUEST_FORM = "infoRequest";
    public static final String DEFAULT_RESPONSE_FORM = "infoResponse";
    public static final String DEFAULT_CASE_FORM = "case";
    public static final String DEFAULT_TASK_FORM = "task";

    private static volatile String defaultReqFormId = DEFAULT_REQUEST_FORM;
    private static volatile String defaultRespFormId = DEFAULT_RESPONSE_FORM;
    private static volatile String defaultCaseFormId = DEFAULT_CASE_FORM;
    private static volatile String defaultTaskFormId = DEFAULT_TASK_FORM;

    /** Point the service at a consumer's request/response/case/task tables. */
    public static void setFormIds(String reqFormId, String respFormId, String caseFormId, String taskFormId) {
        if (reqFormId != null && !reqFormId.trim().isEmpty()) defaultReqFormId = reqFormId.trim();
        if (respFormId != null && !respFormId.trim().isEmpty()) defaultRespFormId = respFormId.trim();
        if (caseFormId != null && !caseFormId.trim().isEmpty()) defaultCaseFormId = caseFormId.trim();
        if (taskFormId != null && !taskFormId.trim().isEmpty()) defaultTaskFormId = taskFormId.trim();
    }

    private final FormDataDao dao;
    private final MmConfigService mm;
    private final CaseEventWriter events;
    private final String fReq;
    private final String fResp;
    private final String fCase;
    private final String fTask;

    public PendingInfoService(FormDataDao dao, MmConfigService mm, CaseEventWriter events) {
        this(dao, mm, events, defaultReqFormId, defaultRespFormId, defaultCaseFormId, defaultTaskFormId);
    }

    public PendingInfoService(FormDataDao dao, MmConfigService mm, CaseEventWriter events,
                              String reqFormId, String respFormId, String caseFormId, String taskFormId) {
        this.dao = dao;
        this.mm = mm;
        this.events = events;
        this.fReq = reqFormId;
        this.fResp = respFormId;
        this.fCase = caseFormId;
        this.fTask = taskFormId;
    }

    public String request(String reqId, String actor) {
        dao.updateSchema(fReq, fReq, new FormRowSet());
        dao.updateSchema(fTask, fTask, new FormRowSet());
        FormRow req = dao.load(fReq, fReq, reqId);
        if (req == null) {
            return "info request not found: " + reqId;
        }
        if (!CaseOpsSupport.prop(req, "result").isEmpty()) {
            return "no-op: already processed";
        }
        String caseId = caseId(req);
        FormRow c = dao.load(fCase, fCase, caseId);
        if (c == null) {
            req.setProperty("result", "case not found: " + caseId);
            save(fReq, req);
            return "case not found: " + caseId;
        }
        String priorState = CaseOpsSupport.prop(c, "currentState");
        String caseType = CaseOpsSupport.prop(c, "caseType");

        FormRow onHold = mm.stateByEnvelope(caseType, "OnHold");
        if (onHold != null) {
            String onHoldCode = onHold.getProperty("code");
            c.setProperty("currentState", onHoldCode);
            save(fCase, c);
            events.append(caseId, "STATE_CHANGED", actor, priorState, onHoldCode,
                    "parked for pending information", null);
        }

        FormRow task = new FormRow();
        task.setId(UUID.randomUUID().toString());
        task.setProperty("caseId", caseId);
        task.setProperty("caseRef", CaseOpsSupport.prop(req, "caseRef"));
        task.setProperty("taskType", "PROVIDE_INFO");
        task.setProperty("status", "OPEN");
        task.setProperty("description", CaseOpsSupport.prop(req, "infoNeeded"));
        save(fTask, task);

        req.setProperty("priorState", priorState);
        req.setProperty("status", "OPEN");
        req.setProperty("taskId", task.getId());
        req.setProperty("result", "REQUESTED");
        save(fReq, req);

        events.append(caseId, "INFO_REQUESTED", actor, "", "",
                "information requested",
                "\"requestId\":\"" + CaseEventWriter.esc(reqId) + "\"");
        events.append(caseId, "NOTIF_PENDING", actor, "", "",
                "pending-information request",
                "\"reason\":\"INFO_REQUESTED\"");
        return "REQUESTED (parked at " + (onHold == null ? priorState : onHold.getProperty("code")) + ")";
    }

    public String respond(String respId, String actor) {
        dao.updateSchema(fResp, fResp, new FormRowSet());
        FormRow resp = dao.load(fResp, fResp, respId);
        if (resp == null) {
            return "info response not found: " + respId;
        }
        if (!CaseOpsSupport.prop(resp, "result").isEmpty()) {
            return "no-op: already processed";
        }
        String requestId = CaseOpsSupport.prop(resp, "requestId");
        FormRow req = requestId.isEmpty() ? null : dao.load(fReq, fReq, requestId);
        if (req == null) {
            resp.setProperty("result", "request not found: " + requestId);
            save(fResp, resp);
            return "request not found: " + requestId;
        }
        String caseId = caseId(req);
        String priorState = CaseOpsSupport.prop(req, "priorState");
        FormRow c = dao.load(fCase, fCase, caseId);
        if (c != null && !priorState.isEmpty()) {
            String current = CaseOpsSupport.prop(c, "currentState");
            c.setProperty("currentState", priorState);
            save(fCase, c);
            events.append(caseId, "STATE_CHANGED", actor, current, priorState,
                    "resumed after information received", null);
        }

        String taskId = CaseOpsSupport.prop(req, "taskId");
        if (!taskId.isEmpty()) {
            FormRow task = dao.load(fTask, fTask, taskId);
            if (task != null) {
                task.setProperty("status", "CLOSED");
                save(fTask, task);
            }
        }

        req.setProperty("status", "RESPONDED");
        save(fReq, req);
        resp.setProperty("result", "RESUMED");
        save(fResp, resp);

        events.append(caseId, "INFO_RECEIVED", actor, "", "",
                "information received",
                "\"requestId\":\"" + CaseEventWriter.esc(requestId) + "\"");
        return "RESUMED (" + priorState + ")";
    }

    private String caseId(FormRow req) {
        String c = CaseOpsSupport.prop(req, "caseId");
        return c.isEmpty() ? CaseOpsSupport.prop(req, "caseRef") : c;
    }

    private void save(String form, FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, form, set);
    }
}
