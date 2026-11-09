package com.fiscaladmin.joget.caseops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
import java.util.List;
import java.util.Map;
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
 * The events HoldService appends are stamped as the platform stamps a row, from the move's own
 * moment (METHOD-2026-11-05-51, R34 version 2, gap G1). The move's moment is the {@code now} the
 * service is called with, 20:25:25 in Europe/Malta, read in the JVM's zone (Europe/Malta here);
 * the event writer's own clock is fixed years later, so an event that carries the move's moment
 * was handed it and did not read its own clock. The event writer is the real one over a mocked
 * FormDataDao and the platform's user is passed in: a person, or nobody.
 */
public class HoldEventStampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 20, 25, 25, 400_000_000);
    static final Date AT = Date.from(NOW.atZone(MALTA).toInstant());           // 2026-11-08T19:25:25.400Z
    static final LocalDateTime LATER = NOW.plusHours(1);
    static final Date LATER_AT = Date.from(LATER.atZone(MALTA).toInstant());
    /** The event writer's own clock, which must not be what an event is stamped from here. */
    static final Clock WRITER_CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), MALTA);

    private TimeZone savedZone;
    private FormDataDao dao;
    private final Map<String, FormRow> holds = new HashMap<String, FormRow>();
    private final Map<String, FormRow> releases = new HashMap<String, FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();

    static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    @Before
    public void setUp() {
        savedZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(MALTA));
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
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(savedZone);
    }

    private HoldService service(final String user, final String name) {
        CaseEventWriter writer = new CaseEventWriter(dao, "caseEvent", WRITER_CLOCK, new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        });
        return new HoldService(dao, writer);
    }

    private void seedHold(String id, String status) {
        FormRow hold = row("caseId", "case-1", "caseRef", "TT-1", "scope", "CORRESPONDENCE_SUPPRESS",
                "holdType", "INFO_PENDING", "basis", "dispute", "targetBB", "CMBB", "status", status);
        hold.setId(id);
        holds.put(id, hold);
    }

    private void seedRelease(String id, String holdId) {
        FormRow rel = row("holdId", holdId, "caseId", "case-1", "releaseReason", "dispute resolved");
        rel.setId(id);
        releases.put(id, rel);
    }

    private FormRow ofType(String type) {
        for (FormRow e : events) {
            if (type.equals(e.getProperty("eventType"))) return e;
        }
        throw new AssertionError("no " + type + " event among " + events.size());
    }

    private static void assertStamped(FormRow e, Date at, String user, String name) {
        String w = e.getProperty("eventType");
        assertEquals(w + " dateCreated", at, e.getDateCreated());
        assertEquals(w + " dateModified", at, e.getDateModified());
        assertEquals(w + " createdBy", user, e.getCreatedBy());
        assertEquals(w + " modifiedBy", user, e.getModifiedBy());
        assertEquals(w + " createdByName", name, e.getCreatedByName());
        assertEquals(w + " modifiedByName", name, e.getModifiedByName());
    }

    @Test
    public void theEventOfAnAssertedHoldIsStampedFromTheMovesNowByThePerson() {
        seedHold("hold-1", "");
        service("anna.grech", "Anna Grech").assertHold("hold-1", "tester", NOW);
        FormRow e = ofType("HOLD_ASSERTED");
        assertStamped(e, AT, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T20:25:25.400", e.getProperty("eventTime"));
        assertTrue(e.getProperty("payload"), e.getProperty("payload").contains("\"timestamp\":\"2026-11-08T20:25:25.400\""));
    }

    @Test
    public void theEventOfAReleasedHoldIsStampedFromTheMovesNowByThePerson() {
        seedHold("hold-1", "ACTIVE");
        seedRelease("rel-1", "hold-1");
        service("anna.grech", "Anna Grech").release("rel-1", "tester", LATER);
        FormRow e = ofType("HOLD_RELEASED");
        assertStamped(e, LATER_AT, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T21:25:25.400", e.getProperty("eventTime"));
    }

    @Test
    public void anAssertAndALaterReleaseEachCarryTheirOwnMoment() {
        seedHold("hold-1", "");
        seedRelease("rel-1", "hold-1");
        HoldService svc = service("anna.grech", "Anna Grech");
        svc.assertHold("hold-1", "tester", NOW);
        svc.release("rel-1", "tester", LATER);
        assertEquals(2, events.size());
        assertStamped(ofType("HOLD_ASSERTED"), AT, "anna.grech", "Anna Grech");
        assertStamped(ofType("HOLD_RELEASED"), LATER_AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theEventsOfAMoveWithNobodySignedInTakeThePlatformsOwnValue() {
        seedHold("hold-1", "");
        seedRelease("rel-1", "hold-1");
        HoldService svc = service("roleAnonymous", null);
        svc.assertHold("hold-1", "tester", NOW);
        svc.release("rel-1", "tester", LATER);
        assertStamped(ofType("HOLD_ASSERTED"), AT, "roleAnonymous", null);
        assertStamped(ofType("HOLD_RELEASED"), LATER_AT, "roleAnonymous", null);
    }

    @Test
    public void theMomentIsTurnedIntoAnInstantInTheJvmsZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));        // restored by tearDown
        seedHold("hold-1", "");
        service("anna.grech", "Anna Grech").assertHold("hold-1", "tester", NOW);
        Date tokyo = Date.from(NOW.atZone(ZoneId.of("Asia/Tokyo")).toInstant());
        assertEquals(tokyo, ofType("HOLD_ASSERTED").getDateModified());
        assertNotEquals("not the Malta reading of the same wall clock", AT, ofType("HOLD_ASSERTED").getDateModified());
    }

    @Test
    public void theChainOfTheEventsAMoveWritesVerifies() {
        seedHold("hold-1", "");
        seedRelease("rel-1", "hold-1");
        HoldService svc = service("anna.grech", "Anna Grech");
        svc.assertHold("hold-1", "tester", NOW);
        svc.release("rel-1", "tester", LATER);
        FormRowSet chain = new FormRowSet();
        chain.addAll(events);
        assertEquals(2, chain.size());
        FormDataDao vdao = mock(FormDataDao.class);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(), any(), anyInt(), anyInt()))
                .thenReturn(chain);
        ChainVerifyService.Result r = new ChainVerifyService(vdao, "caseEvent", "case").verify("case-1");
        assertTrue(r.reason, r.ok);
        assertEquals(2, r.events);
    }
}
