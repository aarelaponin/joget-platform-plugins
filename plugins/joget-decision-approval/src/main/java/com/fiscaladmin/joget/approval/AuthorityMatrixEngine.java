package com.fiscaladmin.joget.approval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

/**
 * VALIDATE gate for the authority matrix. Form post-processor on the {@code cmAuthorityCheck}
 * carrier create: reads the action type, loads its {@code mmAuthority} bands and runs
 * {@link MatrixValidator}, writing {@code valid} / {@code issues} / {@code result} back so a bad
 * authority configuration is caught before it routes a real decision.
 */
public class AuthorityMatrixEngine extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = AuthorityMatrixEngine.class.getName();
    private static final String F_CHECK = "cmAuthorityCheck";
    private static final String F_AUTH = "mmAuthority";

    @Override public String getName() { return "Authority Matrix Validator"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() {
        return "Decision & Approval — mmAuthority band consistency validation.";
    }
    @Override public String getLabel() { return "Authority Matrix Validator"; }
    @Override public String getClassName() { return getClass().getName(); }
    @Override public String getPropertyOptions() { return "[]"; }

    @Override
    public Object execute(Map properties) {
        FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        if (dao == null) {
            throw new RuntimeException("AuthorityMatrixEngine: formDataDao bean not available");
        }
        String id = resolveId(dao, properties);
        FormRow row = id == null ? null : dao.load(F_CHECK, F_CHECK, id);
        if (row == null) {
            LogUtil.warn(CLASS_NAME, "VALIDATE: no check resolvable");
            return null;
        }
        String actionType = Rows.prop(row, "actionType");
        FormRowSet bands = dao.find(F_AUTH, F_AUTH, "WHERE e.customProperties.actionType = ?1",
                new Object[]{actionType}, "dateCreated", Boolean.FALSE, 0, -1);
        List<Map<String, String>> maps = new ArrayList<Map<String, String>>();
        if (bands != null) {
            for (FormRow b : bands) {
                Map<String, String> m = new LinkedHashMap<String, String>();
                for (String f : new String[]{"amountMin", "amountMax", "level", "bodyType",
                        "quorum", "effectiveFrom", "effectiveTo", "version"}) {
                    m.put(f, Rows.prop(b, f));
                }
                maps.add(m);
            }
        }
        MatrixValidator.Result r = MatrixValidator.validate(maps, LocalDate.now().toString());
        row.setProperty("valid", String.valueOf(r.valid));
        row.setProperty("issueCount", String.valueOf(r.issues.size()));
        row.setProperty("issues", String.join("\n", r.issues));
        row.setProperty("result", "VALIDATE " + actionType + " (" + maps.size() + " bands): " + r);
        save(dao, row);
        LogUtil.info(CLASS_NAME, "VALIDATE " + actionType + ": " + r);
        return null;
    }

    private String resolveId(FormDataDao dao, Map properties) {
        String configured = getPropertyString("recordId");
        if (configured != null && !configured.isEmpty() && !configured.startsWith("#")) {
            return configured;
        }
        Object fromMap = properties.get("recordId");
        if (fromMap instanceof String && !((String) fromMap).isEmpty()
                && !((String) fromMap).startsWith("#")) {
            return (String) fromMap;
        }
        dao.updateSchema(F_CHECK, F_CHECK, new FormRowSet());
        FormRowSet rows = dao.find(F_CHECK, F_CHECK,
                "WHERE e.customProperties.result = ?1 OR e.customProperties.result IS NULL",
                new Object[]{""}, "dateCreated", Boolean.TRUE, 0, 1);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0).getId();
    }

    private void save(FormDataDao dao, FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(F_CHECK, F_CHECK, set);
    }
}
