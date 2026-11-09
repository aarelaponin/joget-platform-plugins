package com.fiscaladmin.joget.sla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.eventchain.ChainVerifyService;
import com.fiscaladmin.joget.eventchain.RowStamp;

/**
 * The events the SLA clock engine appends are stamped as the platform stamps a row, from the
 * sweep's own moment (METHOD-2026-11-05-51, R34 version 2, gap G1). The sweep's moment is its
 * {@code asOf}, read in the JVM's zone (Europe/Malta here); the event writer's own clock is fixed
 * years later, so an event that carries the sweep's moment was handed it and did not read its own
 * clock. The event writer is the real one over a mocked FormDataDao and the platform's user is
 * passed in: a person, or nobody. Every kind of event the engine appends is covered: paused,
 * resumed, escalated, breached, the notices and the reassignment.
 */
public class DeadlineEventStampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 1, 9, 0, 0);
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);

    private static Date at(LocalDateTime t) {
        return Date.from(t.atZone(MALTA).toInstant());
    }

    private TimeZone savedZone;
    private FormDataDao dao;
    private final Map<String, FormRow> deadlines = new HashMap<String, FormRow>();
    private final Map<String, FormRow> cases = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();
    private final List<FormRow> slaRows = new ArrayList<FormRow>();
    private final Set<String> terminals = new HashSet<String>();
    private final Map<String, String> envelopes = new HashMap<String, String>();
    private FormRow c;

    static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private final SlaConfig cfg = new SlaConfig() {
        public FormRowSet slaRows(String caseType) {
            FormRowSet s = new FormRowSet();
            for (FormRow r : slaRows) if (caseType.equals(DeadlineService.prop(r, "caseType"))) s.add(r);
            return s;
        }
        public FormRow slaRow(String caseType, String clockCode) {
            for (FormRow r : slaRows)
                if (caseType.equals(DeadlineService.prop(r, "caseType"))
                        && clockCode.equals(DeadlineService.prop(r, "clockCode"))) return r;
            return null;
        }
        public FormRow calendarRow(String calendarCode) { return null; }
        public Set<String> allTerminalCodes() { return terminals; }
        public String envelopeOf(String caseType, String stateCode) {
            return envelopes.get(caseType + "|" + stateCode);
        }
    };

    @Before
    public void setUp() {
        savedZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(MALTA));
        DeadlineService.setEscalationPayloadFields("taxpayerName", "amountAtStake");
        DeadlineService.setSupervisorRole("dm_supervisor");
        dao = mock(FormDataDao.class);
        when(dao.load(eq("case"), eq("case"), anyString())).thenAnswer(i -> cases.get(i.getArguments()[2]));
        when(dao.load(eq("caseDeadline"), eq("caseDeadline"), anyString())).thenAnswer(i -> deadlines.get(i.getArguments()[2]));
        when(dao.find(eq("caseDeadline"), eq("caseDeadline"), anyString(), any(), anyString(), any(), any(), any()))
                .thenAnswer(i -> {
                    Object[] p = (Object[]) i.getArguments()[3];
                    FormRowSet s = new FormRowSet();
                    for (FormRow d : deadlines.values()) {
                        String st = DeadlineService.prop(d, "status");
                        boolean open = "RUNNING".equals(st) || "PAUSED".equals(st);
                        if (p.length == 2) {
                            if (open) s.add(d);
                        } else {
                            if (open && p[0].equals(DeadlineService.prop(d, "caseId"))) s.add(d);
                        }
                    }
                    return s;
                });
        when(dao.find(eq("caseEvent"), eq("caseEvent"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(new FormRowSet());
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) deadlines.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("caseDeadline"), eq("caseDeadline"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) cases.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("case"), eq("case"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) events.add(r); return null; })
                .when(dao).saveOrUpdate(eq("caseEvent"), eq("caseEvent"), any(FormRowSet.class));

        slaRows.add(row("caseType", "TEST", "clockCode", "RES", "durationDays", "10",
                "warnPct", "75", "critPct", "90", "calendar", "", "pauseOnHold", "true",
                "escalationChain", "{\"notify\":[\"assignee\",\"supervisor\"],\"bumpPriority\":true,\"maxLevels\":3}"));
        c = row("caseType", "TEST", "currentState", "OPEN", "caseRef", "TT-000001",
                "assignee", "officer1", "taxpayerName", "Alpha Ltd", "amountAtStake", "500",
                "priority", "0", "slaStatus", "");
        c.setId("d-1");
        cases.put("d-1", c);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(savedZone);
    }

    private DeadlineService service(final String user, final String name) {
        CaseEventWriter writer = new CaseEventWriter(dao, "caseEvent", WRITER_CLOCK, new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        });
        return new DeadlineService(dao, cfg, writer);
    }

    private List<FormRow> ofType(String type) {
        List<FormRow> out = new ArrayList<FormRow>();
        for (FormRow e : events) {
            if (type.equals(e.getProperty("eventType"))) out.add(e);
        }
        assertFalse("no " + type + " event among " + events.size(), out.isEmpty());
        return out;
    }

    private static void assertStamped(FormRow e, Date moment, String user, String name) {
        String w = e.getProperty("eventType");
        assertEquals(w + " dateCreated", moment, e.getDateCreated());
        assertEquals(w + " dateModified", moment, e.getDateModified());
        assertEquals(w + " createdBy", user, e.getCreatedBy());
        assertEquals(w + " modifiedBy", user, e.getModifiedBy());
        assertEquals(w + " createdByName", name, e.getCreatedByName());
        assertEquals(w + " modifiedByName", name, e.getModifiedByName());
    }

    private static void assertAllStamped(List<FormRow> evs, Date moment, String user, String name) {
        assertFalse("some event was written", evs.isEmpty());
        for (FormRow e : evs) assertStamped(e, moment, user, name);
    }

    @Test
    public void thePausedEventIsStampedFromTheSweepsAsOf() {
        envelopes.put("TEST|HOLD", "OnHold");
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        c.setProperty("currentState", "HOLD");
        LocalDateTime asOf = T0.plusDays(2).plusNanos(400_000_000);
        svc.sweep("tester", asOf);
        FormRow e = ofType("SLA_PAUSED").get(0);
        assertStamped(e, at(asOf), "anna.grech", "Anna Grech");
        assertEquals("2026-06-03T09:00:00.400", e.getProperty("eventTime"));
        assertTrue(e.getProperty("payload"), e.getProperty("payload").contains("\"timestamp\":\"2026-06-03T09:00:00.400\""));
    }

    @Test
    public void theResumedEventIsStampedFromTheSweepsAsOf() {
        envelopes.put("TEST|HOLD", "OnHold");
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        c.setProperty("currentState", "HOLD");
        svc.sweep("tester", T0.plusDays(2));
        c.setProperty("currentState", "OPEN");
        LocalDateTime asOf = T0.plusDays(5).plusNanos(250_000_000);
        svc.sweep("tester", asOf);
        assertStamped(ofType("SLA_RESUMED").get(0), at(asOf), "anna.grech", "Anna Grech");
        assertStamped(ofType("SLA_PAUSED").get(0), at(T0.plusDays(2)), "anna.grech", "Anna Grech");
    }

    @Test
    public void theEscalationAndItsNoticesAreStampedFromTheSweepsAsOf() {
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        LocalDateTime asOf = T0.plusDays(9).plusHours(13).plusNanos(600_000_000);
        svc.sweep("tester", asOf);
        assertAllStamped(ofType("SLA_ESCALATED"), at(asOf), "anna.grech", "Anna Grech");
        List<FormRow> notices = ofType("NOTIF_PENDING");
        assertEquals(2, notices.size());
        assertAllStamped(notices, at(asOf), "anna.grech", "Anna Grech");
    }

    @Test
    public void theBreachTheNoticesAndTheReassignmentAreStampedFromTheSweepsAsOf() {
        slaRows.get(0).setProperty("escalationChain",
                "{\"notify\":[\"assignee\"],\"bumpPriority\":false,\"reassignTo\":\"senior1\",\"maxLevels\":3}");
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        LocalDateTime asOf = T0.plusDays(11).plusNanos(700_000_000);
        svc.sweep("tester", asOf);
        assertAllStamped(ofType("SLA_BREACHED"), at(asOf), "anna.grech", "Anna Grech");
        assertAllStamped(ofType("NOTIF_PENDING"), at(asOf), "anna.grech", "Anna Grech");
        assertAllStamped(ofType("CASE_REASSIGNED"), at(asOf), "anna.grech", "Anna Grech");
    }

    @Test
    public void twoSweepsEachCarryTheirOwnAsOf() {
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        LocalDateTime first = T0.plusDays(9).plusHours(13);
        LocalDateTime second = T0.plusDays(11);
        svc.sweep("tester", first);
        svc.sweep("tester", second);
        assertAllStamped(ofType("SLA_ESCALATED"), at(first), "anna.grech", "Anna Grech");
        assertAllStamped(ofType("SLA_BREACHED"), at(second), "anna.grech", "Anna Grech");
    }

    @Test
    public void theEventsOfASweepWithNobodySignedInTakeThePlatformsOwnValue() {
        DeadlineService svc = service("roleAnonymous", null);
        svc.start("d-1", "tester", T0);
        LocalDateTime asOf = T0.plusDays(11);
        svc.sweep("tester", asOf);
        assertTrue("some event was written", events.size() >= 3);
        for (FormRow e : events) assertStamped(e, at(asOf), "roleAnonymous", null);
    }

    @Test
    public void theMomentIsTurnedIntoAnInstantInTheJvmsZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));        // restored by tearDown
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        LocalDateTime asOf = T0.plusDays(11);
        svc.sweep("tester", asOf);
        Date tokyo = Date.from(asOf.atZone(ZoneId.of("Asia/Tokyo")).toInstant());
        assertAllStamped(ofType("SLA_BREACHED"), tokyo, "anna.grech", "Anna Grech");
    }

    @Test
    public void theChainOfTheEventsASweepWritesVerifies() {
        DeadlineService svc = service("anna.grech", "Anna Grech");
        svc.start("d-1", "tester", T0);
        svc.sweep("tester", T0.plusDays(11));
        FormRowSet chain = new FormRowSet();
        chain.addAll(events);
        assertTrue("the sweep wrote more than one event", chain.size() > 1);
        FormDataDao vdao = mock(FormDataDao.class);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt()))
                .thenReturn(chain);
        ChainVerifyService.Result r = new ChainVerifyService(vdao, "caseEvent", "case").verify("d-1");
        assertTrue(r.reason, r.ok);
        assertEquals(chain.size(), r.events);
    }
}
