package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.util.WorkflowUtil;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * The hand-off half of the decision &amp; approval service. Form post-processor on the
 * {@code cmApprovalDelegate} carrier create: reads approvalId + delegateTo + reason and calls
 * {@link ApprovalService#delegate} — recording who the request is reassigned to (and by whom) plus
 * a reasoned event. The delegate still decides under the normal rank gate + SoD; delegation routes
 * the work, it does not confer authority.
 */
public class ApprovalDelegateEngine extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = ApprovalDelegateEngine.class.getName();
    private static final String F_DELEGATE = "cmApprovalDelegate";

    @Override public String getName() { return "Approval Delegate Engine"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() {
        return "Decision & Approval — delegate a pending request to another approver.";
    }
    @Override public String getLabel() { return "Approval Delegate Engine"; }
    @Override public String getClassName() { return getClass().getName(); }
    @Override public String getPropertyOptions() { return "[]"; }

    @Override
    public Object execute(Map properties) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        if (dao == null) {
            throw new RuntimeException("ApprovalDelegateEngine: formDataDao bean not available");
        }
        String fromApprover = WorkflowUtil.getCurrentUsername();
        String decId = resolveDelegate(dao, properties);
        if (decId == null) {
            LogUtil.warn(CLASS_NAME, "DELEGATE: no cmApprovalDelegate resolvable — skipped");
            return null;
        }
        FormRow row = dao.load(F_DELEGATE, F_DELEGATE, decId);
        if (row == null) {
            return null;
        }
        String approvalId = Rows.prop(row, "approvalId");
        String delegateTo = Rows.prop(row, "delegateTo");
        String reason = Rows.prop(row, "reason");
        ApprovalService svc = new ApprovalService(dao, new CaseEventWriter(dao), DecisionEffects.snapshot());
        String result = svc.delegate(approvalId, fromApprover, delegateTo, reason, LocalDateTime.now());
        LogUtil.info(CLASS_NAME, "DELEGATE " + approvalId + " -> " + delegateTo + ": " + result);
        row.setProperty("result", result);
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(F_DELEGATE, F_DELEGATE, set);
        return null;
    }

    private String resolveDelegate(FormDataDao dao, Map properties) {
        String configured = getPropertyString("recordId");
        if (configured != null && !configured.isEmpty() && !configured.startsWith("#")) {
            return configured;
        }
        Object fromMap = properties.get("recordId");
        if (fromMap instanceof String && !((String) fromMap).isEmpty()
                && !((String) fromMap).startsWith("#")) {
            return (String) fromMap;
        }
        dao.updateSchema(F_DELEGATE, F_DELEGATE, new FormRowSet());
        FormRowSet rows = dao.find(F_DELEGATE, F_DELEGATE,
                "WHERE e.customProperties.result = ?1 OR e.customProperties.result IS NULL",
                new Object[]{""}, "dateCreated", Boolean.TRUE, 0, 1);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0).getId();
    }
}
