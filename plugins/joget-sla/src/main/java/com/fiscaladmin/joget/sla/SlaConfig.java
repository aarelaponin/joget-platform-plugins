package com.fiscaladmin.joget.sla;

import java.util.Set;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * The configuration surface the {@link DeadlineService} SLA engine reads. A consumer
 * implements this over its own metamodel tables (SLA clock definitions, working
 * calendars, and the case-type state lifecycle). Keeping it an interface means the
 * SLA engine has no compile dependency on any particular config carrier.
 */
public interface SlaConfig {

    /** All SLA clock definitions for a case type (each row: clockCode, durationDays,
     *  calendar, warnPct, critPct, pauseOnHold, escalationChain). */
    FormRowSet slaRows(String caseType);

    /** One SLA clock definition for (caseType, clockCode), or null. */
    FormRow slaRow(String caseType, String clockCode);

    /** A working-calendar row by code (workingDayMode + holidays), or null. */
    FormRow calendarRow(String calendarCode);

    /** All terminal state codes across the metamodel (a closed clock is MET). */
    Set<String> allTerminalCodes();

    /** The lifecycle envelope of a (caseType, stateCode) — e.g. {@code OnHold}. */
    String envelopeOf(String caseType, String stateCode);
}
