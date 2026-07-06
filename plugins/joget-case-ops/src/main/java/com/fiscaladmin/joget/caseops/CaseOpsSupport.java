package com.fiscaladmin.joget.caseops;

import java.time.format.DateTimeFormatter;

import org.joget.apps.form.model.FormRow;

/** Tiny shared helpers for the case-ops services (kept local so the bundle
 *  carries no dependency on any consumer's utility classes). */
final class CaseOpsSupport {

    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private CaseOpsSupport() {
    }

    /** Trimmed, null-safe property read. */
    static String prop(FormRow r, String id) {
        String v = r == null ? null : r.getProperty(id);
        return v == null ? "" : v.trim();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
