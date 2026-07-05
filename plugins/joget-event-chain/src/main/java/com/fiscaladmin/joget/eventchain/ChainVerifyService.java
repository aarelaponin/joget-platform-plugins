package com.fiscaladmin.joget.eventchain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * Verifies the case event log's tamper-evident hash chain. Read-only: it NEVER
 * mutates the event table.
 *
 * <p>For a case it reads every event ordered by zero-padded seq and checks:
 * <ul>
 *   <li>(a) seq contiguity from 0 (no inserted/deleted rows),</li>
 *   <li>(b) prevHash linkage (each row's prevHash == prior row's hash),</li>
 *   <li>(c) hash recompute: sha256(stored payload + stored prevHash) == stored hash
 *       (a mutated payload changes the recomputed hash -&gt; BROKEN at that seq).</li>
 * </ul>
 * Recompute reuses {@link CaseEventWriter#sha256} and hashes the STORED payload
 * verbatim, so the check is independent of field ordering — it trusts the bytes
 * that were written and only re-derives the hash + the linkage.
 *
 * <p>The event and case table ids are configurable (defaults
 * {@link CaseEventWriter#DEFAULT_EVENT_FORM} and {@value #DEFAULT_CASE_FORM}).
 */
public class ChainVerifyService {

    /** Neutral default case table id. */
    public static final String DEFAULT_CASE_FORM = "case";

    private static volatile String defaultCaseFormId = DEFAULT_CASE_FORM;

    /** Set the process-wide default case table id (call once at consumer start-up). */
    public static void setDefaultCaseFormId(String formId) {
        if (formId != null && !formId.trim().isEmpty()) {
            defaultCaseFormId = formId.trim();
        }
    }

    private static final int FETCH_ALL = 100000;

    private final FormDataDao dao;
    private final String eventFormId;
    private final String caseFormId;

    public ChainVerifyService(FormDataDao dao) {
        this(dao, CaseEventWriter.getDefaultEventFormId(), defaultCaseFormId);
    }

    public ChainVerifyService(FormDataDao dao, String eventFormId, String caseFormId) {
        this.dao = dao;
        this.eventFormId = (eventFormId == null || eventFormId.trim().isEmpty())
                ? CaseEventWriter.getDefaultEventFormId() : eventFormId.trim();
        this.caseFormId = (caseFormId == null || caseFormId.trim().isEmpty())
                ? DEFAULT_CASE_FORM : caseFormId.trim();
    }

    public static class Result {
        public boolean ok = true;
        public long firstBadSeq = -1;
        public String reason = "";
        public int events = 0;
    }

    /** Verify one case's chain. An empty chain is trivially intact. */
    public Result verify(String caseId) {
        dao.updateSchema(eventFormId, eventFormId, new FormRowSet());
        FormRowSet rows = dao.find(eventFormId, eventFormId,
                "WHERE e.customProperties.caseId = ?1", new Object[]{caseId},
                "seq", Boolean.FALSE, 0, FETCH_ALL);
        Result r = new Result();
        if (rows == null || rows.isEmpty()) {
            return r;
        }
        String prevHash = "";
        long expectedSeq = 0;
        for (FormRow row : rows) {
            r.events++;
            long seq = CaseEventWriter.parseSeq(row.getProperty("seq"));
            String payload = nz(row.getProperty("payload"));
            String storedPrev = nz(row.getProperty("prevHash"));
            String storedHash = nz(row.getProperty("hash"));
            if (seq != expectedSeq) {
                return bad(r, seq, "seq gap (expected " + expectedSeq + ")");
            }
            if (!storedPrev.equals(prevHash)) {
                return bad(r, seq, "prevHash linkage broken");
            }
            String recomputed = CaseEventWriter.sha256(payload + storedPrev);
            if (!recomputed.equals(storedHash)) {
                return bad(r, seq, "hash mismatch (payload tampered)");
            }
            prevHash = storedHash;
            expectedSeq = seq + 1;
        }
        return r;
    }

    private Result bad(Result r, long seq, String why) {
        r.ok = false;
        r.firstBadSeq = seq;
        r.reason = why;
        return r;
    }

    /** Distinct caseIds across all cases (for the verify-all / emit scans). */
    public List<String> allCaseIds() {
        dao.updateSchema(caseFormId, caseFormId, new FormRowSet());
        FormRowSet rows = dao.find(caseFormId, caseFormId, null, null,
                "dateCreated", Boolean.FALSE, 0, FETCH_ALL);
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        if (rows != null) {
            for (FormRow r : rows) {
                if (r.getId() != null && !r.getId().isEmpty()) {
                    ids.add(r.getId());
                }
            }
        }
        return new ArrayList<String>(ids);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
