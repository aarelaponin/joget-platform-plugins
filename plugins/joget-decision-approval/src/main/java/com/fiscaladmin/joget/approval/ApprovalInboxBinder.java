package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinderDefault;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

/**
 * The per-user "approvals mine to decide" inbox. A first-class Joget DataListBinder that is
 * API-only: it identifies the user with {@link WorkflowUtil#getCurrentUsername()}, resolves their
 * rank level through {@link AuthorityResolver} (Joget directory API + the mmRoleLevel map — NO raw
 * {@code dir_*} SQL), reads the Pending requests through {@link FormDataDao}, and returns only the
 * requests this user may decide via the pure {@link ApprovalInbox#eligible} rule (rank gate,
 * four-eyes, delegation). The same resolver the gate uses, so the inbox can never show a request the
 * user could not actually approve.
 */
public class ApprovalInboxBinder extends DataListBinderDefault {

    private static final String CLASS_NAME = ApprovalInboxBinder.class.getName();
    private static final String F_APPROVAL = "cmApproval";
    private static final String[][] COLS = {
        {"caseId", "Case"}, {"actionType", "Action"}, {"requiredLevel", "Required level"},
        {"materiality", "Materiality"}, {"requestedBy", "Requested by"}, {"deadline", "Decide by"},
        {"recordId", "Record"}, {"id", "Approval id"}
    };

    @Override public String getName() { return "Approval Inbox Binder"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getDescription() {
        return "Decision & Approval — per-user 'mine to decide' approvals inbox "
                + "(directory-resolved eligibility; API-only).";
    }
    @Override public String getLabel() { return "Approval Inbox Binder"; }
    @Override public String getClassName() { return getClass().getName(); }
    @Override public String getPropertyOptions() { return "[]"; }

    @Override
    public DataListColumn[] getColumns() {
        DataListColumn[] out = new DataListColumn[COLS.length];
        for (int i = 0; i < COLS.length; i++) {
            out[i] = new DataListColumn(COLS[i][0], COLS[i][1], true);
        }
        return out;
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties,
            DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc,
            Integer start, Integer rows) {
        List<Map<String, Object>> elig = eligibleRows();
        DataListCollection c = new DataListCollection();
        int s = start == null ? 0 : Math.max(0, start);
        int n = (rows == null || rows < 0) ? elig.size() : rows;
        for (int i = s; i < elig.size() && (i < s + n); i++) {
            c.add(elig.get(i));
        }
        return c;
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map properties,
            DataListFilterQueryObject[] filterQueryObjects) {
        return eligibleRows().size();
    }

    /** Pending requests this logged-in user may decide — resolved through the directory + the gate rule. */
    private List<Map<String, Object>> eligibleRows() {
        try {
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            if (dao == null) {
                return new ArrayList<Map<String, Object>>();
            }
            String user = WorkflowUtil.getCurrentUsername();
            String level = new AuthorityResolver(dao, AuthorityResolver.directoryGroups()).resolveLevel(user);
            FormRowSet pend = dao.find(F_APPROVAL, F_APPROVAL,
                    "WHERE e.customProperties.status = ?1", new Object[]{"Pending"},
                    "dateCreated", Boolean.FALSE, 0, -1);
            List<Map<String, Object>> all = new ArrayList<Map<String, Object>>();
            if (pend != null) {
                for (FormRow r : pend) {
                    all.add(toRow(r));
                }
            }
            return ApprovalInbox.eligible(all, user, level);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "approval inbox resolution failed");
            return new ArrayList<Map<String, Object>>();
        }
    }

    private Map<String, Object> toRow(FormRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId());
        for (String f : new String[]{"recordId", "actionType", "requiredLevel", "materiality",
                "caseId", "requestedBy", "deadline", "delegatedTo", "status"}) {
            m.put(f, Rows.prop(r, f));
        }
        return m;
    }
}
