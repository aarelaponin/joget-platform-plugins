package com.fiscaladmin.joget.uniqueguard;

import java.util.ArrayList;
import java.util.List;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

/**
 * UniqueGuard — CH-01 WP-H (ADR-072), root-placed per ADR-076.
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
 * Placement (ADR-076): the guard is attached to the Form ROOT validator slot, so it
 * executes on every store path Joget runs and is never skipped for a readonly key
 * (D-067 executes; D-068 cannot bite the root). It derives the form id + table from
 * that root element — there is no formDefId config knob to drift out of the table name
 * (Finding B's seam removed, not re-greased).
 *
 * Config (emitted by project_forms.py from entity.uniqueness; never hand-authored):
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
        return "Declared multi-attribute uniqueness with a scope predicate, enforced at store time on the form root (CH-01 WP-H, ADR-072/ADR-076).";
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
        String attrsCfg = (String) getProperty("attrs");
        String where = (String) getProperty("where");
        String message = (String) getProperty("message");

        // attrs is the only required config; misconfiguration fails open, loudly.
        if (attrsCfg == null || attrsCfg.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "unique-guard misconfigured (attrs missing) — passing");
            return true;
        }
        String[] attrs = attrsCfg.trim().split("\\s*,\\s*");

        // Derive the form id + table from the root the guard is attached to (ADR-076 §2):
        // no formDefId knob to drift out of the table name.
        Form form = FormUtil.findRootForm(element);
        if (form == null) {
            LogUtil.warn(CLASS_NAME, "unique-guard: no root form for element — passing");
            return true;
        }
        String formDefId = form.getPropertyString("id");
        String tableName = form.getPropertyString("tableName");
        if (formDefId == null || formDefId.trim().isEmpty()
                || tableName == null || tableName.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "unique-guard: root form missing id/tableName — passing");
            return true;
        }

        // Build the count condition: every key attribute equals its stored value.
        StringBuilder cond = new StringBuilder("WHERE ");
        List<Object> params = new ArrayList<Object>();
        Element firstKeyEl = null;
        int p = 0;
        for (String attr : attrs) {
            Element keyEl = FormUtil.findElement(attr, form, data);
            if (firstKeyEl == null) {
                firstKeyEl = keyEl;
            }
            // Request param first; a readonly key's value is dropped from the request
            // payload (D-069), so fall back to the row being stored (D-069 fallback).
            String v = data.getRequestParameter(attr);
            if ((v == null || v.trim().isEmpty()) && keyEl != null) {
                v = FormUtil.getElementPropertyValue(keyEl, data);
            }
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
            // Render the refusal beside the key field (its label prints the error span
            // even when the field is readonly) — ADR-076 §4.
            String errorId = FormUtil.getElementParameterName(firstKeyEl != null ? firstKeyEl : element);
            data.addFormError(errorId, message);
            return false;
        }
        return true;
    }
}
