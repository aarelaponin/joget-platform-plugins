package com.fiscaladmin.joget.uniqueguard;

import java.util.ArrayList;
import java.util.List;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

/**
 * UniqueGuard — CH-01 WP-H (ADR-072).
 *
 * A generic, config-driven form validator that enforces a declared multi-attribute
 * uniqueness constraint with an optional scope predicate — the "one OPEN case per TIN"
 * shape. It generalises Joget's built-in DuplicateValueValidator to (a) a composite key
 * and (b) a WHERE scope, counting matching rows at store time and refusing the save with
 * a VAL-02-grade message.
 *
 * It is a FormValidator (store-path, count-then-refuse) — a different slot from the
 * TransitionGuard post-processor, so a lifecycle-bearing entity can carry both.
 *
 * Config (emitted by project_forms.py from entity.uniqueness; never hand-authored):
 *   formDefId : the form / table to count within.
 *   attrs     : comma-separated field ids forming the unique key.
 *   where     : optional scope predicate, ALREADY HQL-qualified by the emitter
 *               (e.g. "e.customProperties.status not in ('closed')").
 *   message   : the refusal message shown to the officer.
 *
 * Fail-open discipline: a misconfigured or erroring guard never blocks a save (it would
 * turn a bug into an outage); it logs and passes. The teeth are on the positive count.
 */
public class UniqueGuard extends FormValidator {

    private static final String CLASS_NAME = UniqueGuard.class.getName();

    @Override
    public String getName() {
        return "Unique Guard";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Declared multi-attribute uniqueness with a scope predicate, enforced at store time (CH-01 WP-H, ADR-072).";
    }

    @Override
    public String getLabel() {
        return "Unique Guard";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/uniqueGuard.json", null, true, null);
    }

    @Override
    public boolean validate(Element element, FormData data, String[] values) {
        String errorId = FormUtil.getElementParameterName(element);
        String formDefId = (String) getProperty("formDefId");
        String attrsCfg = (String) getProperty("attrs");
        String where = (String) getProperty("where");
        String message = (String) getProperty("message");

        // Misconfiguration is not the officer's fault — fail open, loudly.
        if (formDefId == null || formDefId.trim().isEmpty()
                || attrsCfg == null || attrsCfg.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "unique-guard misconfigured (formDefId/attrs missing) — passing");
            return true;
        }

        String[] attrs = attrsCfg.trim().split("\\s*,\\s*");

        // Resolve the physical table for the target form.
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        String tableName = (appDef != null) ? appService.getFormTableName(appDef, formDefId) : formDefId;
        if (tableName == null) {
            LogUtil.warn(CLASS_NAME, "unique-guard: no table for form '" + formDefId + "' — passing");
            return true;
        }

        // Build the count condition: every key attribute equals its submitted value.
        StringBuilder cond = new StringBuilder("WHERE ");
        List<Object> params = new ArrayList<Object>();
        int p = 0;
        for (String attr : attrs) {
            String v = data.getRequestParameter(attr);
            // A null key component cannot collide — nothing to enforce this submit.
            if (v == null || v.trim().isEmpty()) {
                return true;
            }
            if (p > 0) {
                cond.append(" AND ");
            }
            p++;
            cond.append("e.customProperties.").append(attr).append(" = ?").append(p);
            params.add(v.trim());
        }

        // Scope predicate — the emitter provides it HQL-qualified.
        if (where != null && where.trim().length() > 0) {
            cond.append(" AND (").append(where.trim()).append(")");
        }

        // Exclude the record being edited from its own uniqueness check.
        String primaryKey = element.getPrimaryKeyValue(data);
        if (primaryKey != null && primaryKey.trim().length() > 0) {
            p++;
            cond.append(" AND e.id != ?").append(p);
            params.add(primaryKey.trim());
        }

        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        Long n;
        try {
            n = formDataDao.count(formDefId, tableName, cond.toString(), params.toArray());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "unique-guard count failed for form '" + formDefId + "' — passing");
            return true;
        }

        if (n != null && n > 0) {
            if (message == null || message.trim().isEmpty()) {
                message = "A record with these values already exists.";
            }
            data.addFormError(errorId, message);
            return false;
        }
        return true;
    }
}
