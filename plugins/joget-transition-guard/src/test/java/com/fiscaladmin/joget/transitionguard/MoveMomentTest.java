package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The guard takes the move's moment (METHOD-2026-11-05-79, R46; the act R40's G2 left). An engine that runs the
 * guard and then saves the same record reads the clock once and hands that reading in as the property
 * {@code moveMoment}; the guard writes every stamp of the move on that instant and reads no clock. The guard here is
 * the real one over a mocked FormDataDao, the state machine and the event writer are real, and the guard's clock
 * <em>moves</em> - an hour after the moment handed at its first reading and a second more at each next - and counts
 * its readings, so a stamp taken from the clock cannot be the moment handed, and a guard given no moment is seen to
 * read its clock once.
 */
public class MoveMomentTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 9 November 2026, 07:15:30 and 250 ms in Malta (UTC+1): the moment an engine hands. */
    static final Instant MOMENT = Instant.parse("2026-11-09T06:15:30.250Z");
    static final Date AT = Date.from(MOMENT);
    /** The moving clock's first reading: 10:00:00 and 100 ms in Malta. */
    static final Instant FIRST = Instant.parse("2026-11-09T09:00:00.100Z");
    /** The event writer's own clock, which must not be the one an event is stamped from. */
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);
    static final String AUDIT = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,justification=reason,value_before=from,value_after=to,"
            + "move=move,trigger_kind='user'";
    static final String HISTORY = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,value_before=from,trigger_kind='refused'";

    /** A clock that moves one second on every reading of the instant; it counts them. */
    static final class Ticking extends Clock {
        int reads;
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return FIRST.plusSeconds(reads++); }
    }

    private FormDataDao dao;
    private MmConfigService cfg;
    private FormRow act;
    private FormRow record;
    private Ticking clock;

    private static FormRow row(String id, String... kv) {
        FormRow r = new FormRow();
        r.setId(id);
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private static FormRowSet set(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        s.addAll(Arrays.asList(rs));
        return s;
    }

    private static RowStamp.Who who(final String user, final String name) {
        return new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
    }

    private static com.fiscaladmin.joget.eventchain.RowStamp.Who chainWho(final String user, final String name) {
        return new com.fiscaladmin.joget.eventchain.RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
    }

    private TransitionGuard guard() {
        return new TransitionGuard(clock, who("anna.grech", "Anna Grech"));
    }

    private StatusManager manager() {
        return new StatusManager(dao, cfg,
                new CaseEventWriter(dao, "statusEvent", WRITER_CLOCK, chainWho("anna.grech", "Anna Grech")));
    }

    private static Map<String, String> trigger() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "close>closing>closed_on@date,closed_at>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actDcaseClose");
        p.put("actTable", "patmin_case_act");
        p.put("checkRoles", "true");
        p.put("reasonField", "reason");
        p.put("copyFields", "");
        p.put("audit", AUDIT);
        return p;
    }

    @Before
    public void setUp() {
        clock = new Ticking();
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        act = row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "paid");
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0",
                "holder", "carmen.abela");
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        FormRow move = row("m1", "fromStatus", "open", "toStatus", "closing",
                "transitionCode", "close", "roles", "officer,senior");
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(move));
    }

    private String run(Map<String, String> p) {
        return guard().run(p, dao, cfg, manager(), username -> Arrays.asList("officer"), "anna.grech");
    }

    /** The one row saved to {@code table}, or a failure naming how many were saved. */
    private FormRow savedTo(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        assertEquals("one save to " + table, 1, rows.getAllValues().size());
        assertEquals("one row in it", 1, rows.getValue().size());
        return rows.getValue().get(0);
    }

    /** A row stamped at {@code at}: dateModified, and dateCreated where the row was made. */
    private static void assertStampedAt(String what, FormRow r, Date at, boolean made) {
        assertEquals(what + " dateModified", at, r.getDateModified());
        if (made) assertEquals(what + " dateCreated", at, r.getDateCreated());
        assertEquals(what + " modifiedBy", "anna.grech", r.getModifiedBy());
    }

    // ------------------------------------------------------------------- the moment taken

    @Test
    public void aMoveGivenItsMomentWritesEveryRowOnThatInstantAndReadsNoClock() {
        Map<String, String> p = trigger();
        p.put("moveMoment", MOMENT.toString());
        assertEquals("OK: closing", run(p));
        assertSame(record, savedTo("frmCase", "patmin_case"));
        assertStampedAt("record", record, AT, false);
        assertSame(act, savedTo("actDcaseClose", "patmin_case_act"));
        assertStampedAt("act", act, AT, false);
        FormRow history = savedTo("case_history", "patmin_history");
        assertStampedAt("history entry", history, AT, true);
        assertEquals("the history's moment is the moment handed, in the clock's zone", "2026-11-09 07:15:30",
                history.getProperty("moment"));
        FormRow event = savedTo("statusEvent", "statusEvent");
        assertEquals("STATUS_CHANGED", event.getProperty("eventType"));
        assertStampedAt("status event", event, AT, true);
        assertEquals("2026-11-09T07:15:30.250", event.getProperty("eventTime"));
        assertEquals("a set-now moment", "2026-11-09 07:15:30", record.getProperty("closed_at"));
        assertEquals("a set-now day", "2026-11-09", record.getProperty("closed_on"));
        assertEquals("the guard read no clock", 0, clock.reads);
    }

    @Test
    public void aMomentWithAnOffsetIsTheSameInstant() {
        Map<String, String> p = trigger();
        p.put("moveMoment", "2026-11-09T07:15:30.250+01:00");
        assertEquals("OK: closing", run(p));
        assertStampedAt("record", record, AT, false);
        assertEquals("2026-11-09 07:15:30", savedTo("case_history", "patmin_history").getProperty("moment"));
        assertEquals(0, clock.reads);
    }

    @Test
    public void theEngineSurfaceTakesTheMomentToo() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>closed_at>amount eq 0>");
        p.put("audit", AUDIT);
        p.put("moveMoment", MOMENT.toString());
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        assertEquals("OK: closing", guard().run(p, dao, cfg, manager(), username -> Arrays.asList("officer"), "anna.grech"));
        assertSame(record, savedTo("dcase", "patmin_case"));
        assertStampedAt("record", record, AT, false);
        assertStampedAt("history entry", savedTo("case_history", "patmin_history"), AT, true);
        assertEquals(0, clock.reads);
    }

    @Test
    public void aRefusedMoveWritesItsRowsOnTheMomentToo() {
        Map<String, String> p = trigger();
        p.put("moveMoment", MOMENT.toString());
        p.put("audit", "");
        p.put("refused", "audit>" + HISTORY);
        record.setProperty("amount", "250.00");
        String said = run(p);
        assertTrue(said, said.startsWith("REJECTED: "));
        assertStampedAt("refused history entry", savedTo("case_history", "patmin_history"), AT, true);
        assertStampedAt("act", savedTo("actDcaseClose", "patmin_case_act"), AT, false);
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
        assertEquals(0, clock.reads);
    }

    @Test
    public void anActWhoseRecordIsNotFoundIsSavedOnTheMomentToo() {
        Map<String, String> p = trigger();
        p.put("moveMoment", MOMENT.toString());
        when(dao.find(eq("frmCase"), eq("patmin_case"), anyString(), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(new FormRowSet());
        String said = run(p);
        assertTrue(said, said.startsWith("REJECTED: no dcase record"));
        assertStampedAt("act", savedTo("actDcaseClose", "patmin_case_act"), AT, false);
        assertEquals("no clock was read for the act that carries the refusal", 0, clock.reads);
    }

    // ------------------------------------------------------------ the repeated hour (version 2)

    /** In Malta the clocks go back at 2026-10-25T01:00:00Z: 02:30 local is 00:30Z in the first pass and 01:30Z in the second. */
    @Test
    public void aMomentInTheSecondPassOfTheRepeatedHourIsStampedOnThatInstant() {
        Instant secondPass = Instant.parse("2026-10-25T01:30:15.250Z");
        Date at = Date.from(secondPass);
        Map<String, String> p = trigger();
        p.put("moveMoment", secondPass.toString());
        assertEquals("OK: closing", run(p));
        assertStampedAt("record", record, at, false);
        assertStampedAt("act", act, at, false);
        FormRow history = savedTo("case_history", "patmin_history");
        assertStampedAt("history entry", history, at, true);
        assertEquals("2026-10-25 02:30:15", history.getProperty("moment"));
        FormRow event = savedTo("statusEvent", "statusEvent");
        assertStampedAt("status event", event, at, true);
        assertEquals("2026-10-25T02:30:15.250", event.getProperty("eventTime"));
        assertEquals(0, clock.reads);
    }

    @Test
    public void aMomentInTheFirstPassOfTheRepeatedHourIsAnHourEarlierThanTheSameWallClockInTheSecond() {
        Date firstPass = Date.from(Instant.parse("2026-10-25T00:30:15.250Z"));
        Map<String, String> p = trigger();
        p.put("moveMoment", "2026-10-25T02:30:15.250+02:00");
        assertEquals("OK: closing", run(p));
        assertStampedAt("record", record, firstPass, false);
        FormRow history = savedTo("case_history", "patmin_history");
        assertStampedAt("history entry", history, firstPass, true);
        assertEquals("2026-10-25 02:30:15", history.getProperty("moment"));
        assertEquals(0, clock.reads);
    }

    // ------------------------------------------------------------ the guard without a moment

    @Test
    public void withoutAMomentTheGuardReadsItsClockOnceAndStampsAsBefore() {
        assertEquals("OK: closing", run(trigger()));
        Date first = Date.from(FIRST);
        assertEquals("one reading for the whole move", 1, clock.reads);
        assertStampedAt("record", record, first, false);
        assertStampedAt("act", act, first, false);
        FormRow history = savedTo("case_history", "patmin_history");
        assertStampedAt("history entry", history, first, true);
        assertEquals("2026-11-09 10:00:00", history.getProperty("moment"));
        assertEquals("2026-11-09T10:00:00.100", savedTo("statusEvent", "statusEvent").getProperty("eventTime"));
        assertEquals("2026-11-09 10:00:00", record.getProperty("closed_at"));
    }

    @Test
    public void aBlankMomentIsNoMoment() {
        Map<String, String> p = trigger();
        p.put("moveMoment", "   ");
        assertEquals("OK: closing", run(p));
        assertEquals(1, clock.reads);
        assertStampedAt("record", record, Date.from(FIRST), false);
    }

    // ------------------------------------------------------------------- the moment refused

    @Test
    public void aMomentThatDoesNotParseRefusesTheMoveByNameAndWritesNothing() {
        String[] bad = {"yesterday", "2026-11-09 06:15:30", "2026-11-09T06:15:30", "06:15:30Z", "1794000000", "2026-13-09T06:15:30Z"};
        for (String value : bad) {
            Map<String, String> p = trigger();
            p.put("moveMoment", value);
            String said = run(p);
            assertTrue(value + ": " + said, said.startsWith("REJECTED: moveMoment '" + value + "' is not an ISO-8601 instant"));
            assertTrue(value + ": " + said, said.contains("nothing was written"));
        }
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
        verify(dao, never()).load(anyString(), anyString(), anyString());
        assertEquals("the record did not move", "open", record.getProperty("state"));
        assertNull("the record is not stamped", record.getDateModified());
        assertNull("the act is not stamped", act.getDateModified());
        assertNull("the act carries no result", act.getProperty("transition_result"));
        assertEquals("the guard read no clock", 0, clock.reads);
    }
}
