package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormLoadMultiRowElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginManager;

/**
 * A grid's LOAD binder that carries the lines of a request from the record its form is opened on
 * (joget-spec-kit's {@code rowsFrom}; METHOD-2026-10-24-01, the write-off pilot's first run: the
 * request opened with no lines, because the library's generator dropped the key). See
 * {@link GridRows} for what is carried.
 *
 * <p>On a record that exists, and on a submission, it loads exactly as the platform's own
 * {@code org.joget.plugin.enterprise.MultirowFormBinder} loads, with the same {@code formDefId}
 * and {@code foreignKey} - the platform's binder itself, found through the plugin manager - so a
 * saved request shows its own lines and nothing else. On a new record whose own load holds no
 * line, it reads the key from the address and returns the carried lines. Any failure falls back
 * to the platform's load; it never breaks the render. The grid's STORE binder stays the
 * platform's MultirowFormBinder.</p>
 */
public class GridRowsPrefillBinder extends FormBinder
        implements FormLoadElementBinder, FormLoadMultiRowElementBinder {

    static final String PLATFORM_BINDER = "org.joget.plugin.enterprise.MultirowFormBinder";
    private static final int FETCH_LIMIT = 1000;

    @Override
    public String getName() { return "Grid Rows Prefill Binder"; }

    @Override
    public String getVersion() { return "1.1.1"; }

    @Override
    public String getLabel() { return "Grid Rows Prefill Binder"; }

    @Override
    public String getDescription() {
        return "Loads a grid's lines as the platform does; on a new record, carries them from the "
                + "rows of the record the form is opened on.";
    }

    @Override
    public String getClassName() { return this.getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        String json = "[{'title':'Grid Rows Prefill','properties':["
                + "{'name':'formDefId','label':'The grid lines form id','type':'textfield'},"
                + "{'name':'foreignKey','label':'Foreign key field of a line','type':'textfield'},"
                + "{'name':'rowsTable','label':'Table of the rows carried','type':'textfield'},"
                + "{'name':'parentField','label':'Field of a row naming its record','type':'textfield'},"
                + "{'name':'keyParam','label':'Address parameter holding the record key','type':'textfield'},"
                + "{'name':'refField','label':'Field of a line referring to its row','type':'textfield'},"
                + "{'name':'refColumn','label':'Field of the row the line refers by (blank = id)','type':'textfield'},"
                + "{'name':'where','label':'Rows carried only where (HQL over e.customProperties)','type':'textfield'},"
                + "{'name':'map','label':'Values carried','type':'grid',"
                + "'columns':[{'key':'from','label':'From (a field of the row)'},{'key':'to','label':'To (a field of the line)'}]}"
                + "]}]";
        return json.replace('\'', '"');
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet existing = platformLoad(element, primaryKey, formData);
        boolean isNew = primaryKey == null || primaryKey.isEmpty() || "null".equalsIgnoreCase(primaryKey);
        boolean submitting = formData != null && "submit".equals(formData.getRequestParameter("_action"));
        if (!isNew || submitting || (existing != null && !existing.isEmpty())) {
            return existing;
        }
        try {
            GridRows.Config cfg = GridRows.Config.from(this::getProperty);
            String key = formData == null ? null : formData.getRequestParameter(cfg.keyParam);
            if (!cfg.isUsable() || key == null || key.trim().isEmpty()) {
                return existing;
            }
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRowSet found = dao.find(cfg.rowsTable, cfg.rowsTable, GridRows.condition(cfg),
                    new Object[]{key.trim()}, null, Boolean.FALSE, 0, FETCH_LIMIT);
            List<Map<String, String>> rows = new ArrayList<>();
            if (found != null) {
                for (FormRow r : found) {
                    Map<String, String> m = new LinkedHashMap<>();
                    for (Object k : r.keySet()) {
                        m.put(String.valueOf(k), r.getProperty(String.valueOf(k)));
                    }
                    if (r.getId() != null) {
                        m.put("id", r.getId());
                    }
                    rows.add(m);
                }
            }
            FormRowSet out = new FormRowSet();
            out.setMultiRow(true);
            for (Map<String, String> line : GridRows.carry(cfg, rows)) {
                FormRow row = new FormRow();
                for (Map.Entry<String, String> e : line.entrySet()) {
                    row.setProperty(e.getKey(), e.getValue());
                }
                out.add(row);
            }
            return out.isEmpty() ? existing : out;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "GridRowsPrefillBinder failed - falling back to the platform's load");
            return existing;
        }
    }

    /** The grid's own lines, loaded by the platform's MultirowFormBinder with this binder's
     *  formDefId and foreignKey; an empty multi-row set where it cannot be found. */
    private FormRowSet platformLoad(Element element, String primaryKey, FormData formData) {
        try {
            PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            Object p = pm == null ? null : pm.getPlugin(PLATFORM_BINDER);
            if (p instanceof FormLoadBinder && p instanceof ExtDefaultPlugin) {
                Map<String, Object> props = new HashMap<>();
                props.put("formDefId", getPropertyString("formDefId"));
                props.put("foreignKey", getPropertyString("foreignKey"));
                ((ExtDefaultPlugin) p).setProperties(props);
                if (p instanceof FormBinder) {
                    ((FormBinder) p).setElement(element);
                }
                FormRowSet rs = ((FormLoadBinder) p).load(element, primaryKey, formData);
                if (rs != null) {
                    rs.setMultiRow(true);
                    return rs;
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "the platform's MultirowFormBinder could not load the grid");
        }
        FormRowSet empty = new FormRowSet();
        empty.setMultiRow(true);
        return empty;
    }
}
