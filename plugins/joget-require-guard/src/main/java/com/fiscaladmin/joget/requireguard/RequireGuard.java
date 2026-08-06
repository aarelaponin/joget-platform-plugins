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
 * Config (emitted by project_forms.py; never hand-authored):
 *   rules  : one rule per line, pipe-separated, exactly four fields:
 *              whenField|whenEquals|requiredFields(csv)|message
 *            The message is last and is taken verbatim to end of line, so it may contain
 *            pipes; the first three may not (the projector refuses a rule that would).
 *   exists : one rule per line, EIGHT fields — the cross-record precondition:
 *              whenField|whenEquals|form|table|theirAttr|ourAttr|where|message
 *            "When this action is taken, a row must EXIST over there." Refuses when the
 *            count is ZERO, which is the mirror of UniqueGuard's refuse-when-positive.
 *
 * Why `exists` lives here and not in a fourth plugin (2026-08-05): the root carries exactly
 * ONE validator, this guard already owns that slot on every lifecycle form, and it already
 * conditions on `lc_action`. A separate ExistsGuard would have been mutually exclusive with
 * the RequireGuard on the same form — the very collision this file's header already warns
 * about — so the composite the header calls "a follow-on" is simply due.
 *
 * What it closes: registration declared TEN transition guards about ANOTHER record, of which
 * "a passed or explicitly waived safeguard record exists — no TIN is cancelled without one"
 * was PROVED unenforced on 2026-08-05 by walking a deregistration submit -> start_review ->
 * approve with zero safeguard records and having it accepted. Those guards were prose because
 * TransitionGuard.evalGuard reads one attribute of one row. This reads another table.
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

    /**
     * `exists` rules: when the action matches, a row must exist in another table, correlated
     * to this row. Refuses when the count is ZERO.
     *
     * Fail-open on misconfiguration and on error, like every other leg of this plugin: an
     * unreadable table must not turn a bug into an outage. The teeth are on a count that
     * came back and came back empty.
     */
    private boolean checkExists(String cfg, Element root, Form form, FormData data) {
        boolean ok = true;
        for (String line : cfg.split("\\r?\\n")) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\\|", 8);
            if (p.length < 8) {
                LogUtil.warn(CLASS_NAME, "exists-guard: malformed rule '" + line + "' — skipping");
                continue;
            }
            String whenField = p[0].trim(), whenEquals = p[1].trim();
            String otherForm = p[2].trim(), otherTable = p[3].trim();
            String theirAttr = p[4].trim(), ourAttr = p[5].trim();
            String where = p[6].trim(), message = p[7].trim();

            if (whenField.isEmpty() || otherForm.isEmpty() || otherTable.isEmpty()
                    || theirAttr.isEmpty() || ourAttr.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "exists-guard: incomplete rule '" + line + "' — skipping");
                continue;
            }
            if (!whenEquals.equalsIgnoreCase(valueOf(whenField, form, data, root))) {
                continue;               // not this action — nothing to say
            }

            // What we correlate on: `id` means this row's primary key, anything else is a
            // column on this row (request, form, or stored — valueOf covers all three).
            String ourValue = "id".equals(ourAttr)
                    ? root.getPrimaryKeyValue(data)
                    : valueOf(ourAttr, form, data, root);
            if (ourValue == null || ourValue.trim().isEmpty()) {
                // Nothing to correlate on. REFUSE rather than pass: a precondition that cannot
                // be evaluated has not been met, and this guard exists because "could not
                // check" was silently reading as "fine" for the safeguard it protects.
                data.addFormError(FormUtil.getElementParameterName(root), message.isEmpty()
                        ? "This action requires a related record, and this one has no key to look it up by."
                        : message);
                ok = false;
                continue;
            }

            StringBuilder cond = new StringBuilder("WHERE e.customProperties.");
            cond.append(theirAttr).append(" = ?1");
            if (!where.isEmpty()) {
                cond.append(" AND (").append(where).append(")");
            }
            Long n;
            try {
                FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                n = dao.count(otherForm, otherTable, cond.toString(), new Object[]{ourValue.trim()});
            } catch (Exception e) {
                // REFUSE, not pass. This is the one place this plugin departs from the fail-open
                // discipline the rest of it follows, and it is deliberate: a PRECONDITION that
                // could not be evaluated has not been met. On 2026-08-05 the first live run of
                // this guard counted against a column that did not exist (the field was an
                // attribute on no form), threw, passed, and let a deregistration through with no
                // safeguard record — installed, deployed and silently permissive, which is
                // exactly the defect it was written to end. An outage is visible; this was not.
                LogUtil.error(CLASS_NAME, e, "exists-guard count failed on '" + otherForm
                        + "' — REFUSING (a precondition that cannot be checked is not met)");
                data.addFormError(FormUtil.getElementParameterName(root), message.isEmpty()
                        ? "This action requires a related record, and the check could not be run."
                        : message);
                ok = false;
                continue;
            }
            if (n == null || n == 0) {
                data.addFormError(FormUtil.getElementParameterName(root), message.isEmpty()
                        ? "This action requires a related record that does not exist." : message);
                ok = false;
            }
        }
        return ok;
    }

    @Override
    public boolean validate(Element element, FormData data, String[] values) {
        String rulesCfg = (String) getProperty("rules");
        String existsCfg = (String) getProperty("exists");
        boolean haveRules = rulesCfg != null && !rulesCfg.trim().isEmpty();
        boolean haveExists = existsCfg != null && !existsCfg.trim().isEmpty();
        if (!haveRules && !haveExists) {
            LogUtil.warn(CLASS_NAME, "require-guard misconfigured (no rules, no exists) — passing");
            return true;
        }

        Form form = FormUtil.findRootForm(element);
        if (form == null) {
            LogUtil.warn(CLASS_NAME, "require-guard: no root form for element — passing");
            return true;
        }

        boolean ok = haveExists ? checkExists(existsCfg, element, form, data) : true;
        if (!haveRules) {
            return ok;
        }
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
