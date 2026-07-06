package com.fiscaladmin.joget.caseops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * Case linkage service. Validates a typed link against the link-type config's
 * {@code targetCaseTypes}, resolves the target case by {@code caseRef}, and writes
 * the reciprocal row so the case graph is navigable both ways. The reciprocal
 * carries {@code result=RECIPROCAL} so a consumer's unprocessed-row resolver never
 * reprocesses it. Appends {@code CASE_LINKED} to the case event log.
 *
 * <p>Project-neutral: the carrier table ids ({@code linkFormId}, {@code linkTypeFormId},
 * {@code caseFormId}) default to neutral values and are configurable per-instance
 * (settable statics) or per-instance via the full constructor.</p>
 */
public class LinkService {

    /** Neutral default table ids. */
    public static final String DEFAULT_LINK_FORM = "caseLink";
    public static final String DEFAULT_LINKTYPE_FORM = "linkType";
    public static final String DEFAULT_CASE_FORM = "case";

    private static volatile String defaultLinkFormId = DEFAULT_LINK_FORM;
    private static volatile String defaultLinkTypeFormId = DEFAULT_LINKTYPE_FORM;
    private static volatile String defaultCaseFormId = DEFAULT_CASE_FORM;

    /** Point the service at a consumer's link/link-type/case tables (call once at start-up). */
    public static void setFormIds(String linkFormId, String linkTypeFormId, String caseFormId) {
        if (linkFormId != null && !linkFormId.trim().isEmpty()) defaultLinkFormId = linkFormId.trim();
        if (linkTypeFormId != null && !linkTypeFormId.trim().isEmpty()) defaultLinkTypeFormId = linkTypeFormId.trim();
        if (caseFormId != null && !caseFormId.trim().isEmpty()) defaultCaseFormId = caseFormId.trim();
    }

    private final FormDataDao dao;
    private final CaseEventWriter events;
    private final String fLink;
    private final String fLinkType;
    private final String fCase;

    public LinkService(FormDataDao dao, CaseEventWriter events) {
        this(dao, events, defaultLinkFormId, defaultLinkTypeFormId, defaultCaseFormId);
    }

    public LinkService(FormDataDao dao, CaseEventWriter events,
                       String linkFormId, String linkTypeFormId, String caseFormId) {
        this.dao = dao;
        this.events = events;
        this.fLink = linkFormId;
        this.fLinkType = linkTypeFormId;
        this.fCase = caseFormId;
    }

    public String link(String linkId, String actor) {
        dao.updateSchema(fLink, fLink, new FormRowSet());
        FormRow link = dao.load(fLink, fLink, linkId);
        if (link == null) {
            return "link not found: " + linkId;
        }
        if (!CaseOpsSupport.prop(link, "result").isEmpty()) {
            return "no-op: already processed";
        }
        String linkType = CaseOpsSupport.prop(link, "linkType");
        String fromCaseRef = CaseOpsSupport.prop(link, "fromCaseRef");
        String toCaseRef = CaseOpsSupport.prop(link, "toCaseRef");
        String fromCaseId = CaseOpsSupport.prop(link, "fromCaseId");
        if (fromCaseId.isEmpty()) {
            fromCaseId = fromCaseRef;
        }

        List<String> targets = permittedTargets(linkType);
        FormRow toCase = caseByRef(toCaseRef);
        String toCaseId = toCase == null ? "" : toCase.getId();
        String toType = toCase == null ? "" : CaseOpsSupport.prop(toCase, "caseType");

        // target-type validation: blank list = any; only enforceable when the
        // target case is resolvable (cross-domain links may precede the target).
        if (toCase != null && !targets.isEmpty() && !targets.contains(toType)) {
            String result = "REJECTED: " + linkType + " not permitted to type " + toType;
            link.setProperty("result", result);
            save(fLink, link);
            return result;
        }

        link.setProperty("toCaseId", toCaseId);
        boolean reciprocal = toCase != null;
        link.setProperty("reciprocal", String.valueOf(reciprocal));
        link.setProperty("result", "OK");
        save(fLink, link);
        events.append(fromCaseId, "CASE_LINKED", actor, "", "",
                "linked " + linkType + " -> " + toCaseRef,
                "\"linkType\":\"" + CaseEventWriter.esc(linkType) + "\""
                        + ",\"toCaseRef\":\"" + CaseEventWriter.esc(toCaseRef) + "\"");

        if (reciprocal) {
            FormRow rec = new FormRow();
            rec.setId(UUID.randomUUID().toString());
            rec.setProperty("fromCaseId", toCaseId);
            rec.setProperty("fromCaseRef", toCaseRef);
            rec.setProperty("linkType", linkType);
            rec.setProperty("toCaseRef", fromCaseRef);
            rec.setProperty("toCaseId", fromCaseId);
            rec.setProperty("reciprocal", "true");
            rec.setProperty("result", "RECIPROCAL");
            rec.setProperty("note", "reciprocal of " + linkId);
            save(fLink, rec);
            events.append(toCaseId, "CASE_LINKED", actor, "", "",
                    "linked " + linkType + " -> " + fromCaseRef,
                    "\"linkType\":\"" + CaseEventWriter.esc(linkType) + "\""
                            + ",\"toCaseRef\":\"" + CaseEventWriter.esc(fromCaseRef) + "\"");
        }
        return reciprocal ? "OK (reciprocal written)" : "OK (target not yet present)";
    }

    private List<String> permittedTargets(String linkType) {
        FormRowSet rows = dao.find(fLinkType, fLinkType,
                "WHERE e.customProperties.code = ?1", new Object[]{linkType},
                "dateCreated", Boolean.FALSE, 0, 1);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        String csv = CaseOpsSupport.prop(rows.get(0), "targetCaseTypes");
        if (csv.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> out = new ArrayList<String>();
        for (String t : Arrays.asList(csv.split("[,;]"))) {
            if (!t.trim().isEmpty()) {
                out.add(t.trim());
            }
        }
        return out;
    }

    private FormRow caseByRef(String caseRef) {
        if (caseRef == null || caseRef.isEmpty()) {
            return null;
        }
        FormRowSet rows = dao.find(fCase, fCase,
                "WHERE e.customProperties.caseRef = ?1", new Object[]{caseRef},
                "dateCreated", Boolean.FALSE, 0, 1);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }

    private void save(String form, FormRow row) {
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, form, set);
    }
}
