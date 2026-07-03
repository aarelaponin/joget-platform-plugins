package com.fiscaladmin.joget.eventchain;

import org.joget.apps.form.dao.FormDataDao;

/**
 * Generates the human case reference from a case-type's id format.
 * Format convention: literal prefix + a run of '?' as a zero-padded counter,
 * e.g. "TT-??????" -&gt; TT-000001. Counter = count of already-referenced cases
 * of the type + 1, advanced past collisions (DEV-grade sequencing; a dedicated
 * sequence carrier is a recorded hardening item).
 *
 * <p>The case table id is configurable (default {@value #DEFAULT_CASE_FORM}).
 */
public class CaseRefGenerator {

    /** Neutral default case table id. */
    public static final String DEFAULT_CASE_FORM = "case";

    private final FormDataDao dao;
    private final String caseFormId;

    public CaseRefGenerator(FormDataDao dao) {
        this(dao, DEFAULT_CASE_FORM);
    }

    public CaseRefGenerator(FormDataDao dao, String caseFormId) {
        this.dao = dao;
        this.caseFormId = (caseFormId == null || caseFormId.trim().isEmpty())
                ? DEFAULT_CASE_FORM : caseFormId.trim();
    }

    public String generate(String caseTypeCode, String idFormat) {
        int q = idFormat.indexOf('?');
        String prefix = (q < 0) ? idFormat : idFormat.substring(0, q);
        int width = 0;
        for (int i = 0; i < idFormat.length(); i++) {
            if (idFormat.charAt(i) == '?') {
                width++;
            }
        }
        if (width == 0) {
            width = 6;
        }
        Long existing = dao.count(caseFormId, caseFormId,
                "WHERE e.customProperties.caseType = ?1 AND e.customProperties.caseRef <> ?2",
                new Object[]{caseTypeCode, ""});
        long seq = (existing == null ? 0 : existing) + 1;
        String candidate = format(prefix, seq, width);
        // collision guard (counts can lag deletions/imports)
        while (countRef(candidate) > 0) {
            seq++;
            candidate = format(prefix, seq, width);
        }
        return candidate;
    }

    private long countRef(String caseRef) {
        Long n = dao.count(caseFormId, caseFormId,
                "WHERE e.customProperties.caseRef = ?1", new Object[]{caseRef});
        return n == null ? 0 : n;
    }

    private static String format(String prefix, long seq, int width) {
        return prefix + String.format("%0" + width + "d", seq);
    }
}
