package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import com.fiscaladmin.joget.transitionguard.RealGuard;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The repeated hour (METHOD-2026-11-05-79 version 2, G3 of the review of version 1). In Malta the clocks go back on the
 * last Sunday of October at 03:00 summer time: 2026-10-25T01:00:00Z. The local hour 02:00-03:00 happens twice, the first
 * pass at +02:00 (00:00Z-01:00Z) and the second at +01:00 (01:00Z-02:00Z), and a local time in it names two instants.
 * A route that takes its moment as a local time and turns it back into an instant takes the first pass for the second
 * and stamps every row an hour early. The route engine runs the REAL transition guard (as {@code GuardMomentRouteTest}
 * does) under a clock that moves a second on every reading; in each pass every row of the move - the record at the
 * guard's save and at the engine's, the act, the history entry, the status event - must stand on the instant the clock
 * gave on its one reading, whichever pass that is.
 */
public class RepeatedHourRouteTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 02:30:15.250 local, first pass (+02:00). */
    static final Instant FIRST_PASS = Instant.parse("2026-10-25T00:30:15.250Z");
    /** 02:30:15.250 local, second pass (+01:00): the same local time, an hour later. */
    static final Instant SECOND_PASS = Instant.parse("2026-10-25T01:30:15.250Z");
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);
    static final String TABLE = "mtcadm_write_off";
    static final String ROUTES =
            "route_for_approval>awaiting_approval>total_amount>approval_required_from>write_off_approval";
    static final String AUDIT = "route_for_approval>case_history>wo_history>dcase=attr:write_off_reference,"
            + "moment=moment,made_by=actor,value_before=from,value_after=to,move=move";

    /** A clock that moves one second on every reading of the instant; its first reading is {@code first}. */
    static final class Ticking extends Clock {
        final Instant first;
        int reads;
        Ticking(Instant first) { this.first = first; }
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return first.plusSeconds(reads++); }
    }

    private FormDataDao dao;
    private MmConfigService cfg;
    private Ticking clock;
    private FormRow act;
    private FormRow record;
    private final List<Date> recordSaves = new ArrayList<Date>();

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

    private static FormRow band(String min, String max, String level) {
        return row(null, "actionType", "write_off_approval", "amountMin", min, "amountMax", max, "level", level);
    }

    private static com.fiscaladmin.joget.eventchain.RowStamp.Who chainWho() {
        return new com.fiscaladmin.joget.eventchain.RowStamp.Who() {
            @Override public String username() { return "anna.grech"; }
            @Override public String fullName() { return "Anna Grech"; }
        };
    }

    private static RowStamps.Who anna() {
        return new RowStamps.Who() {
            @Override public String username() { return "anna.grech"; }
            @Override public String fullName() { return "Anna Grech"; }
        };
    }

    /** Sets the world up for a clock whose first reading is {@code first}. */
    private void givenAClockAt(Instant first) {
        clock = new Ticking(first);
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        recordSaves.clear();
        act = row("ACT1", "lc_action", "route_for_approval", "record_key", "WO-2026-000001");
        record = row("WO1", "write_off_reference", "WO-2026-000001", "standing", "requested",
                "total_amount", "10427.10");
        when(dao.load("actWriteOffRoute", "mtcadm_write_off_act", "ACT1")).thenReturn(act);
        when(dao.find(eq("frmWriteOff"), eq(TABLE), eq("WHERE e.customProperties.write_off_reference = ?1"),
                any(Object[].class), isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(set(band("0", "1000.00", "officer"), band("1000.01", "20000.00", "senior_officer"),
                        band("20000.01", "", "director")));
        when(cfg.entityScopeHasRules("write_off", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("write_off", "DEFAULT")).thenReturn(set(row(null, "fromStatus", "requested",
                "toStatus", "awaiting_approval", "transitionCode", "route_for_approval", "roles", "")));
        doAnswer(inv -> {
            FormRowSet s = inv.getArgument(2);
            recordSaves.add(s.get(0).getDateModified());
            return null;
        }).when(dao).saveOrUpdate(eq("frmWriteOff"), eq(TABLE), any(FormRowSet.class));
    }

    private Map<String, Object> props() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("entity", "write_off");
        p.put("tableName", TABLE);
        p.put("statusField", "standing");
        p.put("actionField", "lc_action");
        p.put("recordId", "ACT1");
        p.put("transitions", "route_for_approval>awaiting_approval>routed_at>>");
        p.put("routes", ROUTES);
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "write_off_reference");
        p.put("targetForm", "frmWriteOff");
        p.put("actForm", "actWriteOffRoute");
        p.put("actTable", "mtcadm_write_off_act");
        p.put("audit", AUDIT);
        return p;
    }

    private ApprovalRouteEngine engine() {
        return new ApprovalRouteEngine(new RowStamps(clock, anna()));
    }

    private ApprovalRouteEngine.Guard realGuard() {
        final StatusManager sm = new StatusManager(dao, cfg,
                new CaseEventWriter(dao, "statusEvent", WRITER_CLOCK, chainWho()));
        return (className, properties) -> {
            RealGuard.run(clock, "anna.grech", "Anna Grech", properties, dao, cfg, sm,
                    Arrays.asList("officer"), "anna.grech");
            return true;
        };
    }

    private FormRow savedTo(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        return rows.getAllValues().get(rows.getAllValues().size() - 1).get(0);
    }

    private void everyRowOfTheRoutedMoveStandsOn(Instant read) {
        Date at = Date.from(read);
        String said = engine().run(props(), dao, realGuard());
        assertEquals(" · approval level: senior_officer", said);
        assertEquals("the record's dateModified at the guard's save and at the engine's", Arrays.asList(at, at), recordSaves);
        assertEquals("record dateModified", at, record.getDateModified());
        assertEquals("act dateModified", at, act.getDateModified());
        FormRow history = savedTo("case_history", "wo_history");
        assertEquals("history dateCreated", at, history.getDateCreated());
        assertEquals("history dateModified", at, history.getDateModified());
        FormRow event = savedTo("statusEvent", "statusEvent");
        assertEquals("STATUS_CHANGED", event.getProperty("eventType"));
        assertEquals("status event dateCreated", at, event.getDateCreated());
        // the same wall-clock text in both passes: only the instant tells them apart
        assertEquals("the history's moment", "2026-10-25 02:30:15", history.getProperty("moment"));
        assertEquals("status event eventTime", "2026-10-25T02:30:15.250", event.getProperty("eventTime"));
        assertEquals("the guard's set-now moment", "2026-10-25 02:30:15", record.getProperty("routed_at"));
        assertEquals("the clock was read once for the whole route", 1, clock.reads);
    }

    @Test
    public void theTwoPassesShareTheirWallClockAndDifferByAnHour() {
        assertEquals(LocalDateTime.ofInstant(FIRST_PASS, MALTA), LocalDateTime.ofInstant(SECOND_PASS, MALTA));
        assertEquals(3600_000L, SECOND_PASS.toEpochMilli() - FIRST_PASS.toEpochMilli());
    }

    @Test
    public void inTheSecondPassEveryRowOfARoutedMoveStandsOnTheInstantRead() {
        givenAClockAt(SECOND_PASS);
        everyRowOfTheRoutedMoveStandsOn(SECOND_PASS);
    }

    @Test
    public void inTheFirstPassEveryRowOfARoutedMoveStandsOnTheInstantRead() {
        givenAClockAt(FIRST_PASS);
        everyRowOfTheRoutedMoveStandsOn(FIRST_PASS);
    }

    @Test
    public void aMoveTheGuardRefusesInTheSecondPassStampsTheActOnTheInstantRead() {
        givenAClockAt(SECOND_PASS);
        record.setProperty("standing", "closed");
        String said = engine().run(props(), dao, realGuard());
        assertNull(said);
        assertTrue(act.getProperty("transition_result"), act.getProperty("transition_result").startsWith("REJECTED"));
        assertEquals("act dateModified", Date.from(SECOND_PASS), act.getDateModified());
        assertEquals("the clock was read once for the whole route", 1, clock.reads);
    }

    @Test
    public void rowStampsGivesBackTheInstantItReadInEitherPass() {
        for (Instant read : Arrays.asList(FIRST_PASS, SECOND_PASS)) {
            RowStamps stamps = new RowStamps(Clock.fixed(read, MALTA), anna());
            LocalDateTime moment = stamps.now();
            assertEquals(read, stamps.instantOf(moment));
        }
    }

    @Test
    public void anotherRowStampsOfTheSameThreadGivesItBackToo() {
        // the engines and the services each hold a RowStamps of their own; the engine reads, the service turns the moment
        RowStamps engine = new RowStamps(Clock.fixed(SECOND_PASS, MALTA), anna());
        RowStamps service = new RowStamps(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA), anna());
        LocalDateTime moment = engine.now();
        assertEquals(SECOND_PASS, service.instantOf(moment));
    }

    @Test
    public void aLocalTimeThatWasNotReadIsTurnedAsBefore() {
        // the sweep's asOf is given as a local time: no reading to keep, so the zone's rules turn it, as they always did
        RowStamps stamps = new RowStamps(Clock.fixed(SECOND_PASS, MALTA), anna());
        stamps.now();
        assertEquals(Instant.parse("2026-03-01T09:00:00Z"), stamps.instantOf(LocalDateTime.of(2026, 3, 1, 10, 0)));
        assertEquals("an ambiguous local time that was not read takes the earlier offset",
                Instant.parse("2026-10-25T00:00:00Z"), stamps.instantOf(LocalDateTime.of(2026, 10, 25, 2, 0)));
    }
}
