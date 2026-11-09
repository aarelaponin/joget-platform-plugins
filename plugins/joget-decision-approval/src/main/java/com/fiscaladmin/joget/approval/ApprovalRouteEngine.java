package com.fiscaladmin.joget.approval;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ApplicationPlugin;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * The ROUTE half's level: the approval level by amount (METHOD-2026-10-09-05, item 6).
 *
 * <p>A form post-processor standing in the one slot the transition guard would otherwise hold, on a
 * form whose moves include one that routes its record for approval. It runs the transition guard
 * first, by its class name ({@code guardClass}, through the platform's plugin manager; this bundle
 * imports nothing of the guard's), with the properties it was given and one more, {@code moveMoment} (1.6.1): the
 * instant of the engine's own one reading of the clock, which the guard writes on every row of the move in place of
 * a reading of its own, so every move of the form is made exactly as the guard alone makes it, and the record the
 * engine then saves, the act, the guard's history entries and the status event stand on one instant. Then, where the move asked for is a routing
 * move named in {@code routes} and the guard has applied it — the record now stands in the state
 * the move reaches — it reads the record's amount, finds the band of the authority matrix
 * ({@code mmAuthority}) that covers it by the service's own routing
 * ({@link ApprovalService#resolveRoute}), and writes the band's level into the record's level
 * attribute. What it did is added to the move's result.
 *
 * <p>{@code routes}: one line per routing move, {@code move>toState>amountAttr>levelAttr>actionType}
 * — the actionType naming the matrix's bands. A move the guard refused, another move, or a record
 * not found sets nothing. An amount that is not a number, or one no band covers, sets nothing and
 * is said in the result. A guard that is not installed moves nothing, and that is said too.
 */
public class ApprovalRouteEngine extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = ApprovalRouteEngine.class.getName();
    static final String GUARD_CLASS = "com.fiscaladmin.joget.transitionguard.TransitionGuard";
    static final String RESULT_FIELD = "transition_result";
    /** The property the guard reads the move's moment from (1.6.1): the engine's one reading, as an ISO-8601 instant. */
    static final String MOVE_MOMENT = "moveMoment";

    private final RowStamps stamps;

    public ApprovalRouteEngine() {
        this(RowStamps.platform());
    }

    /** The test seam (1.4.0): the clock and the user the rows it saves are stamped with. */
    ApprovalRouteEngine(RowStamps stamps) {
        this.stamps = stamps;
    }

    /** Runs the transition guard: false where no such plugin is installed. The test seam. */
    interface Guard {
        boolean run(String className, Map properties);
    }

    /** The platform's own: the plugin of that class, given the properties, executed. */
    static final Guard PLUGINS = (className, properties) -> {
        PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Plugin p = pm == null ? null : pm.getPlugin(className);
        if (!(p instanceof ApplicationPlugin)) {
            return false;
        }
        if (p instanceof PropertyEditable) {
            ((PropertyEditable) p).setProperties(properties);
        }
        ((ApplicationPlugin) p).execute(properties);
        return true;
    };

    @Override public String getName() { return "Approval Route Engine"; }
    @Override public String getVersion() { return "1.6.1"; }
    @Override public String getDescription() {
        return "Decision & Approval — runs the transition guard, then sets the approval level a routed record's amount calls for.";
    }
    @Override public String getLabel() { return "Approval Route Engine"; }
    @Override public String getClassName() { return getClass().getName(); }
    @Override public String getPropertyOptions() { return "[]"; }

    @Override
    public Object execute(Map properties) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        if (dao == null) {
            LogUtil.warn(CLASS_NAME, "formDataDao unavailable");
            return null;
        }
        run(properties, dao, PLUGINS);
        return null;
    }

    /**
     * The engine's whole work, with its collaborators passed in (the test seam). Returns what it
     * added to the move's result, or the guard's absence, or null where it had nothing to say.
     */
    String run(Map properties, FormDataDao dao, Guard guard) {
        LocalDateTime moment = stamps.now();          // 1.6.0: the move's one reading of the clock
        String entity = str(properties, "entity");
        String tableName = str(properties, "tableName");
        String statusField = def(str(properties, "statusField"), "status");
        String actionField = def(str(properties, "actionField"), "_action");
        String recordId = str(properties, "recordId");
        String targetField = str(properties, "targetField");
        String guardClass = def(str(properties, "guardClass"), GUARD_CLASS);
        boolean known = !entity.isEmpty() && !tableName.isEmpty()
                && !recordId.isEmpty() && !recordId.startsWith("#");

        // 1. the move asked for, read before the guard clears it
        FormRow saved = null;                         // the row this form saved: record or act
        String savedForm = entity;
        String savedTable = tableName;
        if (known) {
            if (targetField.isEmpty()) {
                saved = dao.load(entity, tableName, recordId);
            } else {
                savedForm = def(str(properties, "actForm"), entity);
                savedTable = str(properties, "actTable");
                saved = savedTable.isEmpty() ? null : dao.load(savedForm, savedTable, recordId);
            }
        }
        String action = saved == null ? "" : Rows.prop(saved, actionField).trim();
        String[] route = route(str(properties, "routes"), action);

        // 2. the move itself, made by the transition guard
        // 1.6.1: the guard is handed this engine's one reading, so it reads no clock of its own for the move; the
        // caller's map is left as it was
        Map handed = new HashMap(properties == null ? Collections.emptyMap() : properties);
        handed.put(MOVE_MOMENT, stamps.instantOf(moment).toString());
        if (!guard.run(guardClass, handed)) {
            String said = "ERROR: the transition guard " + guardClass + " is not installed — "
                    + "no move was made";
            LogUtil.warn(CLASS_NAME, said);
            if (saved != null) {
                saved.setProperty(RESULT_FIELD, said);
                save(dao, savedForm, savedTable, saved, moment);
            }
            return said;
        }
        if (route == null || saved == null) {
            return null;
        }

        // 3. the level, where the guard applied the routing move
        String toState = route[1], amountAttr = route[2], levelAttr = route[3], actionType = route[4];
        FormRow record;
        String recordForm;
        if (targetField.isEmpty()) {
            record = dao.load(entity, tableName, recordId);
            recordForm = entity;
        } else {
            recordForm = def(str(properties, "targetForm"), entity);
            record = target(dao, recordForm, tableName, str(properties, "targetKeyColumn"),
                    Rows.prop(saved, targetField));
        }
        if (record == null || !toState.equals(Rows.prop(record, statusField))) {
            return null;                              // refused, or not this record's move
        }
        String raw = Rows.prop(record, amountAttr).trim();
        String said;
        boolean levelled = false;
        double amount = Rows.parseDouble(raw, Double.NaN);
        if (Double.isNaN(amount)) {
            said = " · approval level NOT set: " + amountAttr + " '" + raw + "' is not an amount";
        } else {
            String level = level(dao, actionType, amount, moment);
            if (level == null || level.isEmpty()) {
                said = " · approval level NOT set: no band of " + actionType + " covers " + raw;
            } else {
                record.setProperty(levelAttr, level);
                levelled = true;
                said = " · approval level: " + level;
            }
        }
        if (targetField.isEmpty()) {
            record.setProperty(RESULT_FIELD, Rows.prop(record, RESULT_FIELD) + said);
            save(dao, recordForm, tableName, record, moment);
        } else {
            if (levelled) {
                save(dao, recordForm, tableName, record, moment);
            }
            saved.setProperty(RESULT_FIELD, Rows.prop(saved, RESULT_FIELD) + said);
            save(dao, savedForm, savedTable, saved, moment);
        }
        LogUtil.info(CLASS_NAME, entity + " " + recordId + " " + action + said);
        return said;
    }

    /** The level of the band of {@code actionType} that covers {@code amount} and is in force on the day of
     *  {@code moment} (the engine's one reading), by the service's own routing over {@code mmAuthority};
     *  null where none does. */
    static String level(FormDataDao dao, String actionType, double amount, LocalDateTime moment) {
        ApprovalService svc = new ApprovalService(dao,
                new CaseEventWriter(dao, ApprovalService.F_EVENT),
                Collections.<String, DecisionEffect>emptyMap());
        ApprovalService.Route r = svc.resolveRoute(actionType, amount, moment);
        return r == null ? null : r.firstLevel();
    }

    /** The line of {@code routes} for the move asked for, split on '>', or null. */
    static String[] route(String routes, String action) {
        if (routes == null || action == null || action.isEmpty()) {
            return null;
        }
        for (String line : routes.split("\n")) {
            String[] p = line.trim().split(">", -1);
            if (p.length == 5 && p[0].trim().equals(action)) {
                for (int i = 0; i < p.length; i++) {
                    p[i] = p[i].trim();
                }
                return p;
            }
        }
        return null;
    }

    /** The record an act names: by row id where the key column is empty or {@code id}, else the
     *  one row whose key column holds the key (as the transition guard finds it). */
    static FormRow target(FormDataDao dao, String form, String table, String keyColumn, String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        if (keyColumn == null || keyColumn.isEmpty() || "id".equals(keyColumn)) {
            return dao.load(form, table, key.trim());
        }
        if (!keyColumn.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        FormRowSet rs = dao.find(form, table, "WHERE e.customProperties." + keyColumn + " = ?1",
                new Object[]{key.trim()}, null, Boolean.FALSE, 0, 2);
        return rs == null || rs.size() != 1 ? null : rs.get(0);
    }

    /** 1.4.0: the record's level and result, and the saved act's result, stamped as the platform stamps a row;
     *  1.6.0: at the move's one reading, so the record and the act are one stamp. */
    private void save(FormDataDao dao, String form, String table, FormRow row, LocalDateTime moment) {
        stamps.save(dao, form, table, row, moment);
    }

    private static String str(Map m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? "" : v.toString().trim();
    }

    private static String def(String v, String d) {
        return v == null || v.isEmpty() ? d : v;
    }
}
