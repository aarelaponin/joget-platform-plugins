package com.fiscaladmin.joget.approval;

import org.joget.apps.form.model.FormRow;

/**
 * Null-safe accessors for FormRow properties and numeric parsing. Small shared helper so
 * the approval classes do not borrow static utilities from a consumer's domain services.
 */
public final class Rows {

    private Rows() {
    }

    /** Property value, or "" when the row/value is null. */
    public static String prop(FormRow r, String id) {
        if (r == null) {
            return "";
        }
        String v = r.getProperty(id);
        return v == null ? "" : v;
    }

    public static long parseLong(String s, long dflt) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    public static double parseDouble(String s, double dflt) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }
}
