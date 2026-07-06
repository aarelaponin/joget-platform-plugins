package com.fiscaladmin.joget.sla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/** SLA clock engine — start / traffic lights / escalate-once / pause-resume / close / calendar. */
public class DeadlineServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 1, 9, 0, 0);

    private FormDataDao dao;
    private final Map<String, FormRow> deadlines = new HashMap<String, FormRow>();
    private final Map<String, FormRow> cases = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();
    private final List<FormRow> slaRows = new ArrayList<FormRow>();
    private final Set<String> terminals = new HashSet<String>();
    private final Map<String, String> envelopes = new HashMap<String, String>(); // "type|state" -> envelope
    private FormRow c;
    private DeadlineService svc;

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
                        if (p.length == 2) {                       // sweep: all open
                            if (open) s.add(d);
                        } else {                                   // openClocks: caseId + open
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
        svc = new DeadlineService(dao, cfg, new CaseEventWriter(dao));
    }

    private long ev(String type) {
        return events.stream().filter(e -> type.equals(e.getProperty("eventType"))).count();
    }

    @Test
    public void startCreatesClockFromConfig() {
        assertEquals(1, svc.start("d-1", "tester", T0));
        assertEquals(1, deadlines.size());
        FormRow d = deadlines.values().iterator().next();
        assertEquals("RUNNING", d.getProperty("status"));
        assertEquals("2026-06-11T09:00:00", d.getProperty("dueAt"));
        assertEquals("GREEN", c.getProperty("slaStatus"));
        assertEquals(0, svc.start("d-1", "tester", T0));
    }

    @Test
    public void trafficLightsAcrossThresholds() {
        svc.start("d-1", "tester", T0);
        svc.sweep("tester", T0.plusDays(5));
        assertEquals("GREEN", c.getProperty("slaStatus"));
        svc.sweep("tester", T0.plusDays(8));
        assertEquals("AMBER", c.getProperty("slaStatus"));
        svc.sweep("tester", T0.plusDays(9).plusHours(13));
        assertEquals("RED", c.getProperty("slaStatus"));
    }

    @Test
    public void escalationAtCritThenBreachOnceEach() {
        svc.start("d-1", "tester", T0);
        svc.sweep("tester", T0.plusDays(9).plusHours(13));
        FormRow d = deadlines.values().iterator().next();
        assertEquals("1", d.getProperty("escalationLevel"));
        assertEquals(2, ev("NOTIF_PENDING"));
        assertTrue(ev("SLA_ESCALATED") >= 1);
        assertEquals("1", c.getProperty("priority"));

        svc.sweep("tester", T0.plusDays(11));
        assertEquals("BREACHED", d.getProperty("status"));
        assertEquals("2", d.getProperty("escalationLevel"));
        assertTrue(ev("SLA_BREACHED") >= 1);
        assertEquals("RED", c.getProperty("slaStatus"));

        int before = events.size();
        svc.sweep("tester", T0.plusDays(12));
        long extra = events.stream().skip(before)
                .filter(e -> e.getProperty("eventType").startsWith("SLA_")).count();
        assertEquals(0, extra);
    }

    @Test
    public void pauseOnHoldAndResumeExtendsDue() {
        envelopes.put("TEST|HOLD", "OnHold");
        svc.start("d-1", "tester", T0);
        c.setProperty("currentState", "HOLD");
        svc.sweep("tester", T0.plusDays(2));
        FormRow d = deadlines.values().iterator().next();
        assertEquals("PAUSED", d.getProperty("status"));
        svc.sweep("tester", T0.plusDays(4));
        assertEquals("PAUSED", d.getProperty("status"));
        c.setProperty("currentState", "OPEN");
        svc.sweep("tester", T0.plusDays(5));
        assertEquals("RUNNING", d.getProperty("status"));
        assertEquals("2026-06-14T09:00:00", d.getProperty("dueAt"));
        assertTrue(ev("SLA_RESUMED") >= 1);
    }

    @Test
    public void closedCaseClocksMet() {
        svc.start("d-1", "tester", T0);
        assertEquals(1, svc.close("d-1", "tester"));
        assertEquals("MET", deadlines.values().iterator().next().getProperty("status"));
        assertEquals("-", c.getProperty("slaStatus"));
    }

    @Test
    public void workingDayCalendarSkipsWeekendsAndHolidays() {
        FormRow cal = row("code", "MLT", "workingDayMode", "WORKING", "holidays", "2026-06-08");
        assertEquals(LocalDateTime.of(2026, 6, 9, 9, 0),
                DeadlineService.addDays(LocalDateTime.of(2026, 6, 1, 9, 0), 5, cal));
        assertEquals(LocalDateTime.of(2026, 6, 6, 9, 0),
                DeadlineService.addDays(LocalDateTime.of(2026, 6, 1, 9, 0), 5, null));
    }

    @Test
    public void seniorReassignmentWhenConfigured() {
        slaRows.get(0).setProperty("escalationChain",
                "{\"notify\":[\"assignee\"],\"bumpPriority\":false,\"reassignTo\":\"senior1\",\"maxLevels\":3}");
        svc.start("d-1", "tester", T0);
        svc.sweep("tester", T0.plusDays(11));
        assertEquals("senior1", c.getProperty("assignee"));
        assertEquals("0", c.getProperty("priority"));
        assertTrue(events.stream().anyMatch(e -> "CASE_REASSIGNED".equals(e.getProperty("eventType"))
                && e.getProperty("payload").contains("senior1")));
    }
}
