package com.fiscaladmin.joget.caseops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.List;
import java.util.Map;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/** HoldService — assert/release + suppression scope, over a mocked FormDataDao. */
public class HoldServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 12, 10, 0);
    private FormDataDao dao;
    private final Map<String, FormRow> holds = new HashMap<String, FormRow>();
    private final Map<String, FormRow> releases = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();
    private HoldService svc;

    static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        when(dao.load(eq("hold"), eq("hold"), anyString())).thenAnswer(i -> holds.get(i.getArguments()[2]));
        when(dao.load(eq("holdRelease"), eq("holdRelease"), anyString())).thenAnswer(i -> releases.get(i.getArguments()[2]));
        when(dao.find(eq("caseEvent"), eq("caseEvent"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(new FormRowSet());
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) holds.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("hold"), eq("hold"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) releases.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("holdRelease"), eq("holdRelease"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) events.add(r); return null; })
                .when(dao).saveOrUpdate(eq("caseEvent"), eq("caseEvent"), any(FormRowSet.class));
        when(dao.count(eq("hold"), eq("hold"), anyString(), any())).thenAnswer(i -> {
            Object[] p = (Object[]) i.getArguments()[3];
            long n = 0;
            for (FormRow h : holds.values()) {
                if (p[0].equals(CaseOpsSupport.prop(h, "caseId")) && p[1].equals(CaseOpsSupport.prop(h, "scope"))
                        && "ACTIVE".equals(CaseOpsSupport.prop(h, "status"))) n++;
            }
            return n;
        });
        svc = new HoldService(dao, new CaseEventWriter(dao));
    }

    private long eventCount(String type) {
        return events.stream().filter(e -> type.equals(e.getProperty("eventType"))).count();
    }

    private FormRow seedHold(String id, String scope, String status) {
        FormRow hold = row("caseId", "case-1", "caseRef", "TT-1", "scope", scope,
                "holdType", "INFO_PENDING", "basis", "dispute", "targetBB", "CMBB", "status", status);
        hold.setId(id);
        holds.put(id, hold);
        return hold;
    }

    @Test
    public void assertActivatesAndSuppresses() {
        FormRow hold = seedHold("hold-1", "CORRESPONDENCE_SUPPRESS", "");
        String r = svc.assertHold("hold-1", "tester", NOW);
        assertTrue(r.contains("ACTIVE"));
        assertEquals("ACTIVE", hold.getProperty("status"));
        assertEquals("tester", hold.getProperty("assertedBy"));
        assertEquals(1, eventCount("HOLD_ASSERTED"));
        assertTrue(svc.hasActiveScope("case-1", "CORRESPONDENCE_SUPPRESS"));
    }

    @Test
    public void assertIsIdempotent() {
        seedHold("hold-1", "COLLECTION", "");
        svc.assertHold("hold-1", "tester", NOW);
        String r2 = svc.assertHold("hold-1", "tester", NOW);
        assertTrue(r2.contains("no-op"));
        assertEquals(1, eventCount("HOLD_ASSERTED"));
    }

    @Test
    public void releaseLiftsScope() {
        seedHold("hold-1", "CORRESPONDENCE_SUPPRESS", "ACTIVE");
        FormRow rel = row("holdId", "hold-1", "caseId", "case-1", "releaseReason", "dispute resolved");
        rel.setId("rel-1");
        releases.put("rel-1", rel);
        String r = svc.release("rel-1", "tester", NOW);
        assertTrue(r.contains("RELEASED"));
        assertEquals("RELEASED", holds.get("hold-1").getProperty("status"));
        assertEquals(1, eventCount("HOLD_RELEASED"));
        assertFalse(svc.hasActiveScope("case-1", "CORRESPONDENCE_SUPPRESS"));
    }

    @Test
    public void releaseUnknownHoldIsSafe() {
        FormRow rel = row("holdId", "nope", "releaseReason", "x");
        rel.setId("rel-1");
        releases.put("rel-1", rel);
        String r = svc.release("rel-1", "tester", NOW);
        assertTrue(r.contains("not found"));
        assertEquals(0, eventCount("HOLD_RELEASED"));
    }
}
