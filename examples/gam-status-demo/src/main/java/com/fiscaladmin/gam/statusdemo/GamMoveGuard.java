package com.fiscaladmin.gam.statusdemo;

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
import com.fiscaladmin.joget.statusmanager.InvalidTransitionException;
import com.fiscaladmin.joget.statusmanager.StatusManager;

/**
 * GAM reuse-proof guard. Wired as the post-submission processor on the {@code gamMove}
 * trigger form. On each new gamMove row it drives the PLATFORM state machine:
 * {@link StatusManager#transition} validates {@code gamWidget.status -&gt; targetStatus}
 * against the {@code mmEntityTransition} rules, writes the new status, and appends a
 * {@code STATUS_CHANGED} event to {@code gamEvent} via {@link CaseEventWriter}. An illegal
 * move throws {@link InvalidTransitionException} and nothing is written — the guard records
 * the rejection on the trigger row so the outcome is visible.
 *
 * <p>Domain-agnostic reuse: this plugin names only GAM tables and the platform API. No
 * debt/tax code, no copied platform classes.</p>
 */
public class GamMoveGuard extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = GamMoveGuard.class.getName();
    private static final String F_MOVE = "gamMove";
    private static final String F_WIDGET = "gamWidget";

    @Override
    public String getName() {
        return "GAM Status Move Guard";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Reuse proof — drives the platform StatusManager over a gamWidget entity.";
    }

    @Override
    public String getLabel() {
        return "GAM Status Move Guard";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/gamMoveGuard.json", null, true, null);
    }

    @Override
    public Object execute(Map properties) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        if (dao == null) {
            throw new RuntimeException("GamMoveGuard: formDataDao bean not available");
        }
        String actor = WorkflowUtil.getCurrentUsername();
        if (actor == null || actor.isEmpty()) {
            actor = "system";
        }

        String moveId = resolveMoveId(dao, properties);
        if (moveId == null) {
            LogUtil.warn(CLASS_NAME, "no gamMove row resolvable");
            return null;
        }
        FormRow move = dao.load(F_MOVE, F_MOVE, moveId);
        if (move == null) {
            LogUtil.warn(CLASS_NAME, "gamMove not found: " + moveId);
            return null;
        }

        String widgetId = nz(move.getProperty("widgetId"));
        String target = nz(move.getProperty("targetStatus"));
        String reason = nz(move.getProperty("reason"));

        String result;
        try {
            // The whole point of the proof: a platform bundle drives a GAM entity.
            new StatusManager(dao).transition(
                    F_WIDGET, F_WIDGET, "status", widgetId, widgetId,
                    target, null, actor, reason);
            FormRow w = dao.load(F_WIDGET, F_WIDGET, widgetId);
            result = "OK: gamWidget " + widgetId + " -> "
                    + (w == null ? target : nz(w.getProperty("status")));
        } catch (InvalidTransitionException e) {
            result = "REJECTED: " + e.getMessage();
        } catch (Exception e) {
            result = "ERROR: " + e.getMessage();
            LogUtil.error(CLASS_NAME, e, "transition failed for widget " + widgetId);
        }

        move.setProperty("result", result);
        move.setProperty("resultAt", LocalDateTime.now().toString());
        FormRowSet set = new FormRowSet();
        set.add(move);
        dao.saveOrUpdate(F_MOVE, F_MOVE, set);
        LogUtil.info(CLASS_NAME, "gamMove " + moveId + " -> " + result);
        return null;
    }

    /** The freshly-created trigger row (recordId from the form context), else newest unprocessed. */
    private String resolveMoveId(FormDataDao dao, Map properties) {
        Object fromMap = properties.get("recordId");
        if (fromMap instanceof String && !((String) fromMap).isEmpty()
                && !((String) fromMap).startsWith("#")) {
            return (String) fromMap;
        }
        dao.updateSchema(F_MOVE, F_MOVE, new FormRowSet());
        FormRowSet rows = dao.find(F_MOVE, F_MOVE,
                "WHERE e.customProperties.result = ?1 OR e.customProperties.result IS NULL",
                new Object[]{""}, "dateCreated", Boolean.TRUE, 0, 1);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0).getId();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
