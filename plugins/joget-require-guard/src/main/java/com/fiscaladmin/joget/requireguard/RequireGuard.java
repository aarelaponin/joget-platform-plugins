package com.fiscaladmin.joget.requireguard;

import java.util.ArrayList;
import java.util.List;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

/**
 * RequireGuard — the Joget realization of an entity's `validations` block.
 *
 * A model may declare a conditional requirement ("when lc_action is refuse, rejection_reason
 * is mandatory"). Until now that declaration was emitted ONLY into the RegBB
 * validation-rules.yaml: it validated, generated, deployed — and no generated form enforced
 * it. Registration's REG-FR-041 ("a refusal must carry a reason") was law on paper and
 * nothing at the keyboard; the acceptance case asserting it failed against the running app,
 * which was the correct result and the reason this plugin exists.
 *
 * Slot: the Form ROOT validator, for the same reason as UniqueGuard (ADR-076) — the root
 * always runs (D-067) and cannot be skipped by a readonly or conditionally-hidden field
 * (D-068). A form therefore carries EITHER a UniqueGuard or a RequireGuard; the projector
 * refuses to emit both rather than silently dropping one, and a composite is a follow-on.
 *
 * Config (emitted by project_forms.py from entity.validations; never hand-authored):
 *   rules : one rule per line, pipe-separated, exactly four fields:
 *              whenField|whenEquals|requiredFields(csv)|message
 *           The message is last and is taken verbatim to end of line, so it may contain
 *           pipes; the first three may not (the projector refuses a rule that would).
 *
 * Only the `when` + `require_fields` shape is realized here. `require_grids` / `min_entries`
 * are valid in the model and have no realization on this path; the projector REFUSES them at
 * build time rather than emitting a guard that quietly enforces less than the model declares.
 *
 * Fail-open discipline (same as UniqueGuard): a misconfigured or erroring guard never blocks
 * a save — it logs and passes. The teeth are on the positive finding: the condition held and
 * the required field was empty.
 */
public class RequireGuard extends FormValidator {

    private static final String CLASS_NAME = RequireGuard.class.getName();

    @Override
    public String getName() {
        return "Require Guard";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Conditional field requirements from the model's entities[].validations, enforced at store time on the form root.";
    }

    @Override
    public String getLabel() {
        return "Require Guard";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/requireGuard.json", null, true, null);
    }

    /**
     * The effective value of a field for this save: request parameter, else the element on the
     * form, else the value already stored on the row.
     *
     * The stored-row fallback is not a nicety. The rule is about the RECORD ("a refusal must
     * carry a reason"), not about one request payload, and a required field need not be on the
     * form doing the refusing — the ADR-066 engine surface carries only the action field by
     * design, so a payload-only guard would leave the API path unguarded while the officer's
     * form was guarded. A reason typed yesterday still satisfies the rule today.
     */
    private String valueOf(String fieldId, Form form, FormData data, Element root) {
        String v = data.getRequestParameter(fieldId);
        if (v == null || v.trim().isEmpty()) {
            Element el = FormUtil.findElement(fieldId, form, data);
            if (el != null) {
                v = FormUtil.getElementPropertyValue(el, data);
            }
        }
        if (v == null || v.trim().isEmpty()) {
            v = storedValue(fieldId, form, data, root);
        }
        return v == null ? "" : v.trim();
    }

    /** The column's value on the row being saved, or null when there is no row yet / on error. */
    private String storedValue(String fieldId, Form form, FormData data, Element root) {
        try {
            String primaryKey = root.getPrimaryKeyValue(data);
            String formDefId = form.getPropertyString("id");
            String tableName = form.getPropertyString("tableName");
            if (primaryKey == null || primaryKey.trim().isEmpty()
                    || formDefId == null || formDefId.trim().isEmpty()
                    || tableName == null || tableName.trim().isEmpty()) {
                return null;
            }
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRow row = formDataDao.load(formDefId, tableName, primaryKey.trim());
            return row == null ? null : row.getProperty(fieldId);
        } catch (Exception e) {
            // Fail open, loudly: an unreadable row must not turn a bug into an outage.
            LogUtil.error(CLASS_NAME, e, "require-guard: could not read stored '" + fieldId + "'");
            return null;
        }
    }

    @Override
    public boolean validate(Element element, FormData data, String[] values) {
        String rulesCfg = (String) getProperty("rules");
        if (rulesCfg == null || rulesCfg.trim().isEmpty()) {
            LogUtil.warn(CLASS_NAME, "require-guard misconfigured (no rules) — passing");
            return true;
        }

        Form form = FormUtil.findRootForm(element);
        if (form == null) {
            LogUtil.warn(CLASS_NAME, "require-guard: no root form for element — passing");
            return true;
        }

        boolean ok = true;
        for (String line : rulesCfg.split("\\r?\\n")) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) {
                LogUtil.warn(CLASS_NAME, "require-guard: malformed rule '" + line + "' — skipping");
                continue;
            }
            String whenField = parts[0].trim();
            String whenEquals = parts[1].trim();
            String message = parts[3].trim();

            List<String> required = new ArrayList<String>();
            for (String f : parts[2].split(",")) {
                if (!f.trim().isEmpty()) {
                    required.add(f.trim());
                }
            }
            if (whenField.isEmpty() || required.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "require-guard: rule with no condition or no required field — skipping");
                continue;
            }

            // The condition is evaluated against what is being submitted. A submit that does
            // not carry the condition field at all cannot trip the rule — a rule may only
            // ever refuse a save it can actually see the condition of.
            if (!whenEquals.equalsIgnoreCase(valueOf(whenField, form, data, element))) {
                continue;
            }

            for (String req : required) {
                if (!valueOf(req, form, data, element).isEmpty()) {
                    continue;
                }
                Element reqEl = FormUtil.findElement(req, form, data);
                String errorId = FormUtil.getElementParameterName(reqEl != null ? reqEl : element);
                data.addFormError(errorId, message.isEmpty()
                        ? "This field is required for the selected action." : message);
                ok = false;
            }
        }
        return ok;
    }
}
