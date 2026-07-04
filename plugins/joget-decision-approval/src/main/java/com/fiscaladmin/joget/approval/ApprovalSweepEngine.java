package com.fiscaladmin.joget.approval;

import java.time.LocalDate;
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
 * The time-driven half of the decision &amp; approval service. Form post-processor on the
 * {@code cmApprovalSweep} carrier create: runs {@link ApprovalService#sweep} over the Pending
 * requests as of {@code asOf} (blank = now; a bare date = start of that day — lets an operator
 * time-travel the SLA clock). Overdue requests escalate one rank up, then time out (auto-reject)
 * once escalations are exhausted.
 */
public class ApprovalSweepEngine extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = ApprovalSweepEngine.class.getName();
    private static final String F_SWEEP = "cmApprovalSweep";

    @Override public String getName() { return "Approval Sweep Engine"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() {
        return "Decision & Approval — SLA sweep: escalate then time out overdue requests.";
    }
    @Override public String getLabel() { return "Approval Sweep Engine"; }
    @Override public String getClassName() { return getClass().getName(); }
    @Override public String getPropertyOptions() { return "[]"; }

    @Override
    public Object execute(Map properties) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        if (dao == null) {
            throw new RuntimeException("ApprovalSweepEngine: formDataDao bean not available");
        }
        String actor = WorkflowUtil.getCurrentUsername();
        String runId = resolveRun(dao, properties);
        if (runId == null) {
            LogUtil.warn(CLASS_NAME, "SWEEP: no cmApprovalSweep resolvable — skipped");
            return null;
        }
        FormRow run = dao.load(F_SWEEP, F_SWEEP, runId);
        if (run == null) {
            return null;
        }
        LocalDateTime asOf = parseAsOf(Rows.prop(run, "asOf"));
        ApprovalService svc = new ApprovalService(dao, new CaseEventWriter(dao), DecisionEffects.snapshot());
        String result = svc.sweep(asOf, actor);
        LogUtil.info(CLASS_NAME, "SWEEP: " + result);
        run.setProperty("result", result);
        FormRowSet set = new FormRowSet();
        set.add(run);
        dao.saveOrUpdate(F_SWEEP, F_SWEEP, set);
        return null;
    }

    /** blank -> now; "YYYY-MM-DDThh:mm[:ss]" -> that instant; "YYYY-MM-DD" -> start of day. */
    private LocalDateTime parseAsOf(String s) {
        if (s == null || s.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String v = s.trim();
        try {
            return v.contains("T") ? LocalDateTime.parse(v) : LocalDate.parse(v).atStartOfDay();
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "SWEEP: unparseable asOf '" + v + "' — using now");
            return LocalDateTime.now();
        }
    }

    private String resolveRun(FormDataDao dao, Map properties) {
        String configured = getPropertyString("recordId");
        if (configured != null && !configured.isEmpty() && !configured.startsWith("#")) {
            return configured;
        }
        Object fromMap = properties.get("recordId");
        if (fromMap instanceof String && !((String) fromMap).isEmpty()
                && !((String) fromMap).startsWith("#")) {
            return (String) fromMap;
        }
        dao.updateSchema(F_SWEEP, F_SWEEP, new FormRowSet());
        FormRowSet rows = dao.find(F_SWEEP, F_SWEEP,
                "WHERE e.customProperties.result = ?1 OR e.customProperties.result IS NULL",
                new Object[]{""}, "dateCreated", Boolean.TRUE, 0, 1);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0).getId();
    }
}
