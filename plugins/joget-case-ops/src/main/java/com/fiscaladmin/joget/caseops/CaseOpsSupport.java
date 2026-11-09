package com.fiscaladmin.joget.caseops;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /**
     * A move's moment (the {@code now} a service is called with) as an instant, in the JVM's zone: the zone the
     * platform reads its own clock in, as the transition guard turns its moment (1.2.0; METHOD-2026-11-05-51, R34).
     * The instant a service hands the event writer, so an event's stamps are the move's own moment. Null for null.
     */
    static Instant instantOf(LocalDateTime moment) {
        return moment == null ? null : moment.atZone(ZoneId.systemDefault()).toInstant();
    }
}
