package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
 * The rows the guard writes and changes carry the columns the platform fills when it saves a
 * form's row (METHOD-2026-11-05-45; MT-DM's hand-off of 8 November 2026, item 46), by the rule of
 * {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196): {@code dateModified},
 * {@code modifiedBy} and {@code modifiedByName} always; {@code dateCreated}, {@code createdBy} and
 * {@code createdByName} only where the row holds no {@code dateCreated}. The clock is fixed at an
 * instant that is 20:25:25 in Europe/Malta, and the platform's user is passed in: a person, or
 * nobody (the platform's {@code roleAnonymous}, and no name). FormDataDao, MmConfigService and the
 * event writer are mocked; the state machine is real.
 */
public class StampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 8 November 2026, 20:25:25 and 400 ms in Malta (UTC+1). */
    static final Instant INSTANT = Instant.parse("2026-11-08T19:25:25.400Z");
    static final Clock CLOCK = Clock.fixed(INSTANT, MALTA);
    static final Date AT = Date.from(INSTANT);
    static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(MALTA);
    /** A row the seed loader made on 6 November, by admin. */
    static final Date SEEDED = Date.from(Instant.parse("2026-11-06T12:36:51Z"));

    static final String AUDIT = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,justification=reason,value_before=from,value_after=to,"
            + "move=move,trigger_kind='user'";
    static final String NOTIFY = "close>attr:holder>case_closed>alert>patmin_alert>"
            + "addressed_to=recipient,template=template,about=attr:case_number,channel='email'";
    static final String HISTORY = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,value_before=from,trigger_kind='refused'";
    static final String ALERT = "close>attr:holder>closure_refused>alert>patmin_alert>"
            + "addressed_to=recipient,template=template,about=attr:case_number,channel='internal'";
    static final String RECORDS = "assignment>frmAssignment>patmin_assignment";
    static final String RELATED = "close>assignment>dcase = this and ended_on is null>"
            + "ended_on=moment,end_kind='closure'";
    static final String OPEN_ASSIGNMENT = "WHERE e.customProperties.dcase = ?1 AND "
            + "(e.customProperties.ended_on IS NULL OR e.customProperties.ended_on = '')";

    private FormDataDao dao;
    private MmConfigService cfg;
    private StatusManager sm;
    private FormRow act;
    private FormRow record;

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

    /** The row as the platform left it when the seed loader (admin) saved it on 6 November. */
    private static FormRow seeded(FormRow r) {
        r.setDateCreated(SEEDED);
        r.setCreatedBy("admin");
        r.setCreatedByName("Administrator");
        r.setDateModified(SEEDED);
        r.setModifiedBy("admin");
        r.setModifiedByName("Administrator");
        return r;
    }

    private static RowStamp.Who who(final String user, final String name) {
        return new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
    }

    private static TransitionGuard guard(RowStamp.Who who) {
        return new TransitionGuard(CLOCK, who);
    }

    private static RowStamp.Who anna() {
        return who("anna.grech", "Anna Grech");
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
        p.put("records", RECORDS);
        p.put("thisKey", "case_number");
        return p;
    }

    private static GroupLookup holds(String... groups) {
        return username -> Arrays.asList(groups);
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        sm = new StatusManager(dao, cfg, mock(CaseEventWriter.class));
        act = seeded(row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "paid"));
        record = seeded(row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0",
                "holder", "carmen.abela"));
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        FormRow move = row("m1", "fromStatus", "open", "toStatus", "closing",
                "transitionCode", "close", "roles", "officer,senior");
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(move));
    }

    /** The one row written to {@code table}, or a failure naming how many were. */
    private FormRow written(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        List<FormRowSet> all = rows.getAllValues();
        assertEquals("one save to " + table, 1, all.size());
        assertEquals("one row in it", 1, all.get(0).size());
        return all.get(0).get(0);
    }

    /** A row made: all six columns, from the one instant and the platform's user. */
    private static void assertMade(String what, FormRow r, String user, String name) {
        assertEquals(what + " dateCreated", AT, r.getDateCreated());
        assertEquals(what + " dateModified", AT, r.getDateModified());
        assertEquals(what + " createdBy", user, r.getCreatedBy());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " createdByName", name, r.getCreatedByName());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
    }

    /** A row changed: the modified three moved, the created three as the row was loaded. */
    private static void assertChanged(String what, FormRow r, String user, String name) {
        assertEquals(what + " dateModified", AT, r.getDateModified());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
        assertEquals(what + " dateCreated kept", SEEDED, r.getDateCreated());
        assertEquals(what + " createdBy kept", "admin", r.getCreatedBy());
        assertEquals(what + " createdByName kept", "Administrator", r.getCreatedByName());
    }

    // ------------------------------------------------------------ the rows the guard makes

    @Test
    public void aHistoryEntryAndAnAlertAreStampedAsNewRowsFromTheMovesInstant() {
        Map<String, String> p = trigger();
        p.put("audit", AUDIT);
        p.put("notify", NOTIFY);
        assertEquals("OK: closing", guard(anna()).run(p, dao, cfg, sm, holds("officer"), "anna.grech"));
        FormRow h = written("case_history", "patmin_history");
        assertMade("history entry", h, "anna.grech", "Anna Grech");
        assertMade("alert", written("alert", "patmin_alert"), "anna.grech", "Anna Grech");
        // the moment and the stamp are one reading, in Malta's zone
        assertEquals("2026-11-08 20:25:25", h.getProperty("moment"));
        assertEquals(h.getProperty("moment"), MOMENT.format(h.getDateCreated().toInstant()));
    }

    @Test
    public void aRefusedMovesHistoryEntryAndAlertAreStampedToo() {
        Map<String, String> p = trigger();
        p.put("refused", "audit>" + HISTORY + "\nnotify>" + ALERT);
        record.setProperty("amount", "250.00");
        String result = guard(anna()).run(p, dao, cfg, sm, holds("officer"), "anna.grech");
        assertTrue(result, result.startsWith("REJECTED: "));
        FormRow h = written("case_history", "patmin_history");
        assertMade("refused history entry", h, "anna.grech", "Anna Grech");
        assertMade("refused alert", written("alert", "patmin_alert"), "anna.grech", "Anna Grech");
        assertEquals("2026-11-08 20:25:25", h.getProperty("moment"));
        // the record did not move and is not saved; the act, which carries the result, is
        assertEquals("the refused record is untouched", SEEDED, record.getDateModified());
        verify(dao, never()).saveOrUpdate(eq("frmCase"), anyString(), any(FormRowSet.class));
        assertChanged("act", written("actDcaseClose", "patmin_case_act"), "anna.grech", "Anna Grech");
    }

    @Test
    public void aRefusalSaidBeforeTheSaveWritesItsRowsStampedToo() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("transitions", "close>closing>>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("fromState", "open");
        p.put("refused", "audit>" + HISTORY);
        p.put("refusals", "amount eq 0|Cannot close: {case_number} still owes {amount}");
        record.setProperty("amount", "250.00");
        String words = new MoveRefusal(guard(anna())).refuse(p, dao, "anna.grech", "DC-000001", "");
        assertEquals("Cannot close: DC-000001 still owes 250.00", words);
        FormRow h = written("case_history", "patmin_history");
        assertMade("validator's history entry", h, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08 20:25:25", h.getProperty("moment"));
    }

    // ---------------------------------------------------------- the rows the guard changes

    @Test
    public void theRecordMovedAndTheActSavedAreStampedAsChangedRows() {
        assertEquals("OK: closing",
                guard(anna()).run(trigger(), dao, cfg, sm, holds("officer"), "anna.grech"));
        assertSame(record, written("frmCase", "patmin_case"));
        assertChanged("record", record, "anna.grech", "Anna Grech");
        assertSame(act, written("actDcaseClose", "patmin_case_act"));
        assertChanged("act", act, "anna.grech", "Anna Grech");
    }

    @Test
    public void aRowLoadedWithNoDateCreatedGetsItsCreatedThreeAsWellAsTheModifiedThree() {
        record.setDateCreated(null);
        record.setCreatedBy(null);
        record.setCreatedByName(null);
        guard(anna()).run(trigger(), dao, cfg, sm, holds("officer"), "anna.grech");
        assertMade("record with no dateCreated", record, "anna.grech", "Anna Grech");
        assertChanged("act, which had one", act, "anna.grech", "Anna Grech");
    }

    @Test
    public void theEngineSurfaceStampsTheRecordItSaves() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "case-uuid");
        p.put("transitions", "close>closing>closed_on@date>>|open_case>open>>>");
        p.put("audit", AUDIT);
        record.setProperty("lc_action", "close");
        when(dao.load("dcase", "patmin_case", "case-uuid")).thenReturn(record);
        assertEquals("OK: closing",
                guard(anna()).run(p, dao, cfg, sm, mock(GroupLookup.class), "anna.grech"));
        assertSame(record, written("dcase", "patmin_case"));
        assertChanged("record", record, "anna.grech", "Anna Grech");
        assertMade("history entry", written("case_history", "patmin_history"), "anna.grech", "Anna Grech");
    }

    @Test
    public void theRowsARelatedLineSetsAreStampedAsChangedRows() {
        Map<String, String> p = trigger();
        p.put("related", RELATED);
        FormRow a1 = seeded(row("as-1", "dcase", "DC-000001", "held_by", "carmen.abela"));
        when(dao.find(eq("frmAssignment"), eq("patmin_assignment"), eq(OPEN_ASSIGNMENT),
                any(Object[].class), isNull(), isNull(), isNull(), isNull())).thenReturn(set(a1));
        assertEquals("OK: closing", guard(anna()).run(p, dao, cfg, sm, holds("officer"), "anna.grech"));
        assertSame(a1, written("frmAssignment", "patmin_assignment"));
        assertChanged("assignment", a1, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08 20:25:25", a1.getProperty("ended_on"));
    }

    @Test
    public void anActNamingNoRecordIsSavedWithItsResultAndStampedAsAChangedRow() {
        act.setProperty("record_key", "DC-999999");
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(new FormRowSet());
        String result = guard(anna()).run(trigger(), dao, cfg, sm, holds("officer"), "anna.grech");
        assertTrue(result, result.startsWith("REJECTED: no dcase record 'DC-999999'"));
        assertChanged("act", written("actDcaseClose", "patmin_case_act"), "anna.grech", "Anna Grech");
    }

    @Test
    public void aMoveTheConfigurationDoesNotNameWritesNothing() {
        act.setProperty("lc_action", "reopen");
        assertNull(guard(anna()).run(trigger(), dao, cfg, sm, holds("officer"), "anna.grech"));
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
    }

    // ------------------------------------------------------------------ one instant a move

    /** A clock that moves on at every reading, and counts them. */
    static final class Ticking extends Clock {
        int readings;
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { readings++; return INSTANT.plusSeconds(7L * readings); }
    }

    @Test
    public void theMovesMomentAndEveryRowsStampAreOneReadingOfTheClock() {
        Ticking clock = new Ticking();
        Map<String, String> p = trigger();
        p.put("audit", AUDIT);
        p.put("notify", NOTIFY);
        p.put("related", RELATED);
        FormRow a1 = seeded(row("as-1", "dcase", "DC-000001"));
        when(dao.find(eq("frmAssignment"), eq("patmin_assignment"), eq(OPEN_ASSIGNMENT),
                any(Object[].class), isNull(), isNull(), isNull(), isNull())).thenReturn(set(a1));
        new TransitionGuard(clock, anna()).run(p, dao, cfg, sm, holds("officer"), "anna.grech");
        assertEquals("the clock is read once for the move", 1, clock.readings);
        Date first = Date.from(INSTANT.plusSeconds(7));
        FormRow h = written("case_history", "patmin_history");
        assertEquals(first, h.getDateCreated());
        assertEquals(first, written("alert", "patmin_alert").getDateCreated());
        assertEquals(first, record.getDateModified());
        assertEquals(first, act.getDateModified());
        assertEquals(first, a1.getDateModified());
        // 19:25:32 UTC is 20:25:32 in Malta: the moment is that reading, not a second one
        assertEquals("2026-11-08 20:25:32", h.getProperty("moment"));
        assertEquals("2026-11-08 20:25:32", a1.getProperty("ended_on"));
        assertEquals("2026-11-08", record.getProperty("closed_on"));
        assertEquals("2026-11-08 20:25:32", record.getProperty("closed_at"));
    }

    // ------------------------------------------------- a move with nobody signed in (item 46 (a))

    @Test
    public void aMoveWithNoPersonIsMadeBySystemAndStampedWithThePlatformsOwnValue() {
        Map<String, String> p = trigger();
        p.put("audit", AUDIT);
        p.put("notify", NOTIFY);
        p.put("checkRoles", "false");
        p.put("transitions", "close>closing>closed_on@date,closed_at,closed_by@actor>amount eq 0>");
        TransitionGuard g = guard(who("roleAnonymous", null));
        String actor = g.actor();
        assertEquals("system", actor);
        assertEquals("OK: closing", g.run(p, dao, cfg, sm, holds(), actor));
        FormRow h = written("case_history", "patmin_history");
        assertEquals("the history says the solution made the move", "system", h.getProperty("made_by"));
        assertEquals("system", record.getProperty("closed_by"));
        // the stamps keep the platform's own value, and no name where there is no user
        assertMade("history entry", h, "roleAnonymous", null);
        assertMade("alert", written("alert", "patmin_alert"), "roleAnonymous", null);
        assertEquals("roleAnonymous", record.getModifiedBy());
        assertNull(record.getModifiedByName());
        assertEquals("admin", record.getCreatedBy());
    }

    @Test
    public void theActorOfAMoveIsThePlatformsUserOrTheSolutionsWordWhereThereIsNoPerson() {
        assertEquals("anna.grech", TransitionGuard.actorOf("anna.grech"));
        assertEquals("system", TransitionGuard.actorOf(null));
        assertEquals("system", TransitionGuard.actorOf(""));
        assertEquals("system", TransitionGuard.actorOf("roleAnonymous"));
        assertEquals("roleSystem is a user the platform names, and stays", "roleSystem",
                TransitionGuard.actorOf("roleSystem"));
        assertEquals("a name that merely contains it stays", "roleAnonymousX",
                TransitionGuard.actorOf("roleAnonymousX"));
    }

    @Test
    public void theGuardsActorIsWhatThePlatformSaysOrSystemWhereThereIsNoPerson() {
        assertEquals("anna.grech", guard(who("anna.grech", "Anna Grech")).actor());
        assertEquals("system", guard(who("roleAnonymous", null)).actor());
        assertEquals("system", guard(who("", null)).actor());
        assertEquals("system", guard(who(null, null)).actor());
        assertEquals("the platform's roleSystem is a user it names", "roleSystem",
                guard(who("roleSystem", null)).actor());
    }

    @Test
    public void theValidatorNamesAMoveWithNoPersonSystemToo() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("transitions", "close>closing>>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("fromState", "open");
        p.put("refused", "audit>" + HISTORY);
        p.put("refusals", "amount eq 0|Cannot close: {case_number} still owes {amount}");
        record.setProperty("amount", "250.00");
        TransitionGuard g = guard(who("roleAnonymous", null));
        new MoveRefusal(g).refuse(p, dao, g.actor(), "DC-000001", "");
        FormRow h = written("case_history", "patmin_history");
        assertEquals("system", h.getProperty("made_by"));
        assertMade("history entry", h, "roleAnonymous", null);
    }

    @Test
    public void whereThePlatformsUserCannotBeReadTheStampLeavesTheWhoColumnsEmptyAndTheMoveStands() {
        // no application context in a unit test: the platform's own Who answers none
        assertNull(RowStamp.PLATFORM.username());
        assertNull(RowStamp.PLATFORM.fullName());
        Map<String, String> p = trigger();
        p.put("audit", AUDIT);
        assertEquals("OK: closing",
                new TransitionGuard(CLOCK, RowStamp.PLATFORM).run(p, dao, cfg, sm, holds("officer"), "anna"));
        FormRow h = written("case_history", "patmin_history");
        assertEquals(AT, h.getDateCreated());
        assertNull(h.getCreatedBy());
        assertNull(h.getModifiedByName());
    }
}
