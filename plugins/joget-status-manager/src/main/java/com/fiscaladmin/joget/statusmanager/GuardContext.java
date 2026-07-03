package com.fiscaladmin.joget.statusmanager;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.eventchain.CaseRefGenerator;

/**
 * Everything a guard phase needs, constructor-injected for testability
 * (phases never reach for AppUtil themselves).
 *
 * <p>The case/task/doc table ids are neutral by default ({@code case}/{@code task}/
 * {@code doc}); a consumer whose carriers are named differently sets them once at
 * start-up via {@link #setFormIds} so its phases keep reading {@link #F_CASE} etc.
 */
public class GuardContext {

    /** Case table id (mutable default; override once via setFormIds). */
    public static String F_CASE = "case";
    /** Task table id. */
    public static String F_TASK = "task";
    /** Document table id. */
    public static String F_DOC = "doc";

    /** Set the process-wide case/task/doc table ids (call once at consumer start-up). */
    public static void setFormIds(String caseFormId, String taskFormId, String docFormId) {
        if (caseFormId != null && !caseFormId.trim().isEmpty()) F_CASE = caseFormId.trim();
        if (taskFormId != null && !taskFormId.trim().isEmpty()) F_TASK = taskFormId.trim();
        if (docFormId != null && !docFormId.trim().isEmpty()) F_DOC = docFormId.trim();
    }

    private final FormDataDao dao;
    private final MmConfigService mm;
    private final CaseEventWriter events;
    private final CaseRefGenerator refs;
    private final String caseId;
    private final String actor;
    private final boolean requireDecision;
    private FormRow caseRow;

    public GuardContext(FormDataDao dao, MmConfigService mm, CaseEventWriter events,
                        CaseRefGenerator refs, String caseId, String actor,
                        boolean requireDecision) {
        this.dao = dao;
        this.mm = mm;
        this.events = events;
        this.refs = refs;
        this.caseId = caseId;
        this.actor = actor;
        this.requireDecision = requireDecision;
    }

    public FormDataDao dao() { return dao; }
    public MmConfigService mm() { return mm; }
    public CaseEventWriter events() { return events; }
    public CaseRefGenerator refs() { return refs; }
    public String caseId() { return caseId; }
    public String actor() { return actor; }
    public boolean requireDecision() { return requireDecision; }

    /** The case row under guard; loaded once, cached. */
    public FormRow caseRow() {
        if (caseRow == null) {
            caseRow = dao.load(F_CASE, F_CASE, caseId);
            if (caseRow == null) {
                throw new IllegalStateException("case row not found: " + caseId);
            }
        }
        return caseRow;
    }

    public String prop(String fieldId) {
        String v = caseRow().getProperty(fieldId);
        return v == null ? "" : v;
    }

    public void saveCase() {
        FormRowSet set = new FormRowSet();
        set.add(caseRow());
        dao.saveOrUpdate(F_CASE, F_CASE, set);
    }
}
