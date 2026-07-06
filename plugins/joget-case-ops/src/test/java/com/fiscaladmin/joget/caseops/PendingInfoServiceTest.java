package com.fiscaladmin.joget.caseops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.fiscaladmin.joget.statusmanager.MmConfigService;

/** PendingInfoService — park/resume loop over a mocked FormDataDao + MmConfigService. */
public class PendingInfoServiceTest {

    private FormDataDao dao;
    private final Map<String, FormRow> reqs = new HashMap<String, FormRow>();
    private final Map<String, FormRow> resps = new HashMap<String, FormRow>();
    private final Map<String, FormRow> cases = new HashMap<String, FormRow>();
    private final Map<String, FormRow> tasks = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();
    private PendingInfoService svc;

    static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        when(dao.load(eq("infoRequest"), eq("infoRequest"), anyString())).thenAnswer(i -> reqs.get(i.getArguments()[2]));
        when(dao.load(eq("infoResponse"), eq("infoResponse"), anyString())).thenAnswer(i -> resps.get(i.getArguments()[2]));
        when(dao.load(eq("case"), eq("case"), anyString())).thenAnswer(i -> cases.get(i.getArguments()[2]));
        when(dao.load(eq("task"), eq("task"), anyString())).thenAnswer(i -> tasks.get(i.getArguments()[2]));
        // MmConfigService.stateByEnvelope reads mmState: return the OnHold state row
        FormRow onHold = row("caseType", "TEST", "code", "AWAIT_INFO", "envelopeState", "OnHold");
        when(dao.find(eq("mmState"), eq("mmState"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(new FormRowSet() {{ add(onHold); }});
        when(dao.find(eq("caseEvent"), eq("caseEvent"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(new FormRowSet());
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) reqs.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("infoRequest"), eq("infoRequest"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) resps.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("infoResponse"), eq("infoResponse"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) cases.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("case"), eq("case"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) tasks.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("task"), eq("task"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) events.add(r); return null; })
                .when(dao).saveOrUpdate(eq("caseEvent"), eq("caseEvent"), any(FormRowSet.class));

        svc = new PendingInfoService(dao, new MmConfigService(dao), new CaseEventWriter(dao));
        FormRow c = row("caseType", "TEST", "caseRef", "TT-1", "currentState", "INPROGRESS");
        c.setId("case-1");
        cases.put("case-1", c);
    }

    private long ev(String type) {
        return events.stream().filter(e -> type.equals(e.getProperty("eventType"))).count();
    }

    @Test
    public void requestParksThenResponseResumes() {
        FormRow req = row("caseId", "case-1", "caseRef", "TT-1", "infoNeeded", "bank statements", "result", "");
        req.setId("req-1");
        reqs.put("req-1", req);

        String r1 = svc.request("req-1", "tester");
        assertTrue(r1.contains("REQUESTED"));
        assertEquals("AWAIT_INFO", cases.get("case-1").getProperty("currentState"));
        assertEquals("INPROGRESS", req.getProperty("priorState"));
        assertEquals(1, tasks.size());
        FormRow task = tasks.values().iterator().next();
        assertEquals("PROVIDE_INFO", task.getProperty("taskType"));
        assertEquals("OPEN", task.getProperty("status"));
        assertEquals(1, ev("INFO_REQUESTED"));
        assertEquals(1, ev("NOTIF_PENDING"));

        FormRow resp = row("requestId", "req-1", "responseSummary", "provided", "result", "");
        resp.setId("resp-1");
        resps.put("resp-1", resp);

        String r2 = svc.respond("resp-1", "tester");
        assertTrue(r2.contains("RESUMED"));
        assertEquals("INPROGRESS", cases.get("case-1").getProperty("currentState"));
        assertEquals("CLOSED", task.getProperty("status"));
        assertEquals("RESPONDED", req.getProperty("status"));
        assertEquals(1, ev("INFO_RECEIVED"));
    }
}
