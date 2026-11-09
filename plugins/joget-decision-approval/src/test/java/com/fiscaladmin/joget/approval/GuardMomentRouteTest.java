package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A routed move takes one moment (METHOD-2026-11-05-79, R46; the act R40's G2 left). The route engine runs the REAL
 * transition guard (joget-transition-guard, a test-scope dependency; {@link RealGuard} is its test door) over a mocked
 * FormDataDao, with the real state machine and the real event writer. The one clock moves - a second on every reading of
 * the instant, the first being the engine's - and counts its readings, so a stamp taken from a second reading, whichever
 * side took it, differs by a second. The record the guard moves and the engine then saves, the act, the guard's history
 * entry and the status event must stand on the engine's one instant, and the clock must have been read once for the whole
 * route.
 */
public class GuardMomentRouteTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 9 November 2026, 07:15:30 and 250 ms in Malta (UTC+1): the engine's one reading. */
    static final Instant FIRST = Instant.parse("2026-11-09T06:15:30.250Z");
    static final Date AT = Date.from(FIRST);
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);
    static final String TABLE = "mtcadm_write_off";
    static final String ROUTES =
            "route_for_approval>awaiting_approval>total_amount>approval_required_from>write_off_approval";
    static final String AUDIT = "route_for_approval>case_history>wo_history>dcase=attr:write_off_reference,"
            + "moment=moment,made_by=actor,value_before=from,value_after=to,move=move";

    /** A clock that moves one second on every reading of the instant; its first reading is {@link #FIRST}. */
    static final class Ticking extends Clock {
        int reads;
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return FIRST.plusSeconds(reads++); }
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

    @Before
    public void setUp() {
        clock = new Ticking();
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
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
            recordSaves.add(s.get(0).getDateModified());     // the record's dateModified at each save
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
        return new ApprovalRouteEngine(new RowStamps(clock, new RowStamps.Who() {
            @Override public String username() { return "anna.grech"; }
            @Override public String fullName() { return "Anna Grech"; }
        }));
    }

    /** The real guard, as the platform's plugin manager would run it for the engine. */
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

    @Test
    public void aRoutedRecordItsActTheHistoryEntryAndTheStatusEventAreOneInstantAndTheClockIsReadOnce() {
        String said = engine().run(props(), dao, realGuard());
        assertEquals(" \u00b7 approval level: senior_officer", said);
        assertEquals("the guard moved the record", "awaiting_approval", record.getProperty("standing"));
        assertEquals("and the engine levelled it", "senior_officer", record.getProperty("approval_required_from"));

        assertEquals("the record's dateModified at the guard's save and at the engine's", Arrays.asList(AT, AT), recordSaves);
        assertEquals("record dateModified", AT, record.getDateModified());
        assertEquals("act dateModified", AT, act.getDateModified());
        assertTrue(act.getProperty("transition_result"), act.getProperty("transition_result").contains("approval level: senior_officer"));

        FormRow history = savedTo("case_history", "wo_history");
        assertEquals("history dateCreated", AT, history.getDateCreated());
        assertEquals("history dateModified", AT, history.getDateModified());
        assertEquals("the history's moment", "2026-11-09 07:15:30", history.getProperty("moment"));
        FormRow event = savedTo("statusEvent", "statusEvent");
        assertEquals("STATUS_CHANGED", event.getProperty("eventType"));
        assertEquals("status event dateCreated", AT, event.getDateCreated());
        assertEquals("status event eventTime", "2026-11-09T07:15:30.250", event.getProperty("eventTime"));
        assertEquals("the guard's set-now moment", "2026-11-09 07:15:30", record.getProperty("routed_at"));

        assertEquals("the clock was read once for the whole route", 1, clock.reads);
    }

    @Test
    public void theRouteOfAMoveTheGuardRefusesReadsTheClockOnceToo() {
        record.setProperty("standing", "closed");        // the move does not leave this state: refused
        String said = engine().run(props(), dao, realGuard());
        assertNull(said);
        assertEquals("closed", record.getProperty("standing"));
        assertNull(record.getProperty("approval_required_from"));
        assertTrue(act.getProperty("transition_result"), act.getProperty("transition_result").startsWith("REJECTED"));
        assertEquals("act dateModified", AT, act.getDateModified());
        assertEquals("the clock was read once for the whole route", 1, clock.reads);
    }
}
