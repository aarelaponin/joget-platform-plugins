package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Configurable form LOAD binder. On opening a NEW record it resolves a key (from an
 * ordered list of sources — request parameter, logged-in user, …), looks up a source
 * record through Joget's {@code FormDataDao} (no raw SQL), optionally loads related
 * records, maps their fields onto this form, and returns a synthetic row so the form
 * opens pre-populated. Opening an EXISTING record (edit/view) defers entirely to the
 * default load — untouched. Any failure falls back to the default load; it never breaks
 * the render.
 *
 * <p>All behaviour is driven by plugin properties ({@link PrefillConfig}); the prefill
 * logic lives in {@link PrefillResolver}. One JAR, configured per form — no Java per use.</p>
 */
public class FormPrefillLoadBinder extends WorkflowFormBinder {

    private static final int FETCH_LIMIT = 10000;

    @Override
    public String getName() { return "Form Prefill Load Binder"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getLabel() { return "Form Prefill Load Binder"; }

    @Override
    public String getDescription() {
        return "Pre-populates a new form from a related record, by configuration only.";
    }

    @Override
    public String getClassName() { return this.getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        // JSON written with single quotes, swapped to double quotes — avoids escape noise.
        String json = "[{'title':'Form Prefill','properties':["
                + "{'name':'enabled','label':'Enabled','type':'selectbox','value':'true',"
                + "'options':[{'value':'true','label':'Yes'},{'value':'false','label':'No'}]},"
                + "{'name':'keySources','label':'Key sources (ordered, first non-empty wins)','type':'grid',"
                + "'columns':[{'key':'source','label':'Source (requestParam|loginUser|currentField)'},{'key':'name','label':'Name'}]},"
                + "{'name':'formId','label':'Lookup form id','type':'textfield'},"
                + "{'name':'table','label':'Lookup table (blank = form id)','type':'textfield'},"
                + "{'name':'matchField','label':'Field that equals the key','type':'textfield'},"
                + "{'name':'filters','label':'Extra filters','type':'grid',"
                + "'columns':[{'key':'field','label':'Field'},{'key':'op','label':'op (eq|ne)'},{'key':'value','label':'Value'}]},"
                + "{'name':'orderBy','label':'Order by field (pick first)','type':'textfield'},"
                + "{'name':'orderNumeric','label':'Order numerically','type':'selectbox','value':'false',"
                + "'options':[{'value':'true','label':'Yes'},{'value':'false','label':'No'}]},"
                + "{'name':'related','label':'Related loads (dao.load by id)','type':'grid',"
                + "'columns':[{'key':'formId','label':'Form id'},{'key':'table','label':'Table'},{'key':'keyFrom','label':'Key from (primary field)'},{'key':'alias','label':'Alias'}]},"
                + "{'name':'mappings','label':'Field mapping (from: key | primaryField | alias.field)','type':'grid',"
                + "'columns':[{'key':'from','label':'From'},{'key':'to','label':'To (this form field)'}]},"
                + "{'name':'constants','label':'Constants','type':'grid',"
                + "'columns':[{'key':'to','label':'To'},{'key':'value','label':'Value'}]},"
                + "{'name':'onlyOnAdd','label':'Only on add','type':'selectbox','value':'true',"
                + "'options':[{'value':'true','label':'Yes'},{'value':'false','label':'No'}]}"
                + "]}]";
        return json.replace('\'', '"');
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet existing = super.load(element, primaryKey, formData);
        boolean hasRecord = existing != null && !existing.isEmpty();
        boolean isAdd = primaryKey == null || primaryKey.isEmpty() || "null".equalsIgnoreCase(primaryKey);
        if (hasRecord || !isAdd) {
            return existing; // edit / view — defer entirely to the default load
        }
        try {
            PrefillConfig cfg = PrefillConfig.from(this::getProperty);
            if (!cfg.isUsable()) {
                return existing;
            }
            List<String> candidates = new ArrayList<>();
            for (PrefillConfig.KeySource ks : cfg.keySources) {
                candidates.add(resolveKeySource(formData, ks));
            }
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            if (dao == null) {
                return existing;
            }
            Map<String, String> values = PrefillResolver.resolve(cfg, candidates, new DaoAccess(dao));
            if (values.isEmpty()) {
                return existing;
            }
            FormRow row = new FormRow();
            for (Map.Entry<String, String> e : values.entrySet()) {
                row.setProperty(e.getKey(), e.getValue());
            }
            FormRowSet rs = new FormRowSet();
            rs.add(row);
            return rs;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "FormPrefillLoadBinder failed — falling back to default load");
            return existing;
        }
    }

    private String resolveKeySource(FormData formData, PrefillConfig.KeySource ks) {
        if (ks == null || ks.source == null) {
            return null;
        }
        String src = ks.source.trim();
        if ("requestParam".equalsIgnoreCase(src) || "currentField".equalsIgnoreCase(src)) {
            return formData == null ? null : formData.getRequestParameter(ks.name);
        }
        if ("loginUser".equalsIgnoreCase(src)) {
            return WorkflowUtil.getCurrentUsername();
        }
        return null; // sessionAttr / workflowVar — reserved, not yet resolved
    }

    /** FormDataDao-backed {@link DataAccess}. The field name is already sanitised upstream. */
    static final class DaoAccess implements DataAccess {
        private final FormDataDao dao;
        DaoAccess(FormDataDao dao) { this.dao = dao; }

        @Override
        public List<Map<String, String>> findByField(String formId, String table, String matchField, String key) {
            List<Map<String, String>> out = new ArrayList<>();
            FormRowSet rs = dao.find(formId, table,
                    "WHERE e.customProperties." + matchField + " = ?1",
                    new Object[]{key}, null, Boolean.FALSE, 0, FETCH_LIMIT);
            if (rs != null) {
                for (FormRow r : rs) {
                    out.add(toMap(r));
                }
            }
            return out;
        }

        @Override
        public Map<String, String> loadById(String formId, String table, String id) {
            if (id == null || id.trim().isEmpty()) {
                return null;
            }
            FormRow r = dao.load(formId, table, id.trim());
            return r == null ? null : toMap(r);
        }

        private static Map<String, String> toMap(FormRow r) {
            Map<String, String> m = new LinkedHashMap<>();
            for (Object k : r.keySet()) {
                String name = String.valueOf(k);
                m.put(name, r.getProperty(name));
            }
            if (r.getId() != null) {
                m.put("id", r.getId());
            }
            return m;
        }
    }
}
