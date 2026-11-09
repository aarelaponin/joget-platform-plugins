package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.eventchain.ChainVerifyService;
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
 * The status event a move appends is stamped as the platform stamps a row, from the move's own
 * instant (METHOD-2026-11-05-51, R34; MT-DM's answer of 8 November 2026 to item 46's acceptance).
 * The guard's clock is fixed at an instant that is 20:25:25 in Europe/Malta; the event writer's
 * own clock is fixed years later, so an event carrying the guard's instant was handed it and
 * did not read its own. The event writer is the real one over a mocked FormDataDao, the state
 * machine is real, and the platform's user is passed in: a person, or nobody.
 */
public class EventStampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 8 November 2026, 20:25:25 and 400 ms in Malta (UTC+1). */
    static final Instant INSTANT = Instant.parse("2026-11-08T19:25:25.400Z");
    static final Clock GUARD_CLOCK = Clock.fixed(INSTANT, MALTA);
    static final Date AT = Date.from(INSTANT);
    /** The event writer's own clock, which must not be the one an event is stamped from here. */
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);
    static final String AUDIT = "close>case_history>patmin_history>dcase=attr:case_number,"
            + "moment=moment,made_by=actor,justification=reason,value_before=from,value_after=to,"
            + "move=move,trigger_kind='user'";

    private FormDataDao dao;
    private MmConfigService cfg;
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

    private StatusManager manager(String user, String name) {
        return new StatusManager(dao, cfg,
                new CaseEventWriter(dao, "statusEvent", WRITER_CLOCK, chainWho(user, name)));
    }

    private FormRow savedTo(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        assertEquals("one save to " + table, 1, rows.getAllValues().size());
        return rows.getValue().get(0);
    }

    private static void assertSixStamps(String what, FormRow r, String user, String name) {
        assertEquals(what + " dateCreated", AT, r.getDateCreated());
        assertEquals(what + " dateModified", AT, r.getDateModified());
        assertEquals(what + " createdBy", user, r.getCreatedBy());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " createdByName", name, r.getCreatedByName());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
    }

    @Test
    public void theStatusEventOfAMoveIsStampedFromTheMovesInstantByThePerson() {
        TransitionGuard g = new TransitionGuard(GUARD_CLOCK, who("anna.grech", "Anna Grech"));
        assertEquals("OK: closing", g.run(trigger(), dao, cfg, manager("anna.grech", "Anna Grech"),
                username -> Arrays.asList("officer"), "anna.grech"));
        FormRow e = savedTo("statusEvent", "statusEvent");
        assertEquals("STATUS_CHANGED", e.getProperty("eventType"));
        assertSixStamps("status event", e, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T20:25:25.400", e.getProperty("eventTime"));
    }

    @Test
    public void theStatusEventAndTheHistoryEntryOfAMoveAreOneReading() {
        TransitionGuard g = new TransitionGuard(GUARD_CLOCK, who("anna.grech", "Anna Grech"));
        g.run(trigger(), dao, cfg, manager("anna.grech", "Anna Grech"),
                username -> Arrays.asList("officer"), "anna.grech");
        FormRow e = savedTo("statusEvent", "statusEvent");
        FormRow h = savedTo("case_history", "patmin_history");
        assertEquals("the event and the history entry are stamped alike", h.getDateCreated(), e.getDateCreated());
        assertEquals(h.getDateModified(), e.getDateModified());
        assertEquals("2026-11-08 20:25:25", h.getProperty("moment"));
    }

    @Test
    public void theStatusEventOfAMoveWithNobodySignedInTakesThePlatformsOwnValue() {
        TransitionGuard g = new TransitionGuard(GUARD_CLOCK, who("roleAnonymous", null));
        g.run(trigger(), dao, cfg, manager("roleAnonymous", null),
                username -> Arrays.asList("officer"), g.actor());
        FormRow e = savedTo("statusEvent", "statusEvent");
        assertSixStamps("status event", e, "roleAnonymous", null);
        assertEquals("system", e.getProperty("actor"));
    }

    @Test
    public void aRefusedMoveWritesNoStatusEvent() {
        TransitionGuard g = new TransitionGuard(GUARD_CLOCK, who("anna.grech", "Anna Grech"));
        String said = g.run(trigger(), dao, cfg, manager("anna.grech", "Anna Grech"),
                username -> Arrays.asList("clerk"), "anna.grech");   // holds none of the move's roles
        assertTrue(said, said.startsWith("REJECTED"));
        verify(dao, never()).saveOrUpdate(eq("statusEvent"), anyString(), any(FormRowSet.class));
    }

    @Test
    public void theChainOfTheEventsAMoveWritesVerifies() {
        TransitionGuard g = new TransitionGuard(GUARD_CLOCK, who("anna.grech", "Anna Grech"));
        g.run(trigger(), dao, cfg, manager("anna.grech", "Anna Grech"),
                username -> Arrays.asList("officer"), "anna.grech");
        FormRowSet chain = new FormRowSet();
        chain.add(savedTo("statusEvent", "statusEvent"));
        FormDataDao vdao = mock(FormDataDao.class);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(chain);
        ChainVerifyService.Result r = new ChainVerifyService(vdao, "statusEvent", "case").verify("case-uuid");
        assertTrue(r.reason, r.ok);
        assertEquals(1, r.events);
    }
}
