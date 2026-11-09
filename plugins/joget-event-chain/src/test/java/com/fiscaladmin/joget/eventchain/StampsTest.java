package com.fiscaladmin.joget.eventchain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
 * The event rows {@link CaseEventWriter} writes carry the columns the platform fills when it saves
 * a form's row (METHOD-2026-11-05-51, R34; MT-DM's hand-off of 8 November 2026, answer to item 46),
 * by the rule of {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196), as R32
 * applied it to the guard's and the route's rows: all six on a new row, from one instant. The clock
 * is fixed at an instant that is 20:25:25 in Europe/Malta; the platform's user is passed in, a
 * person or nobody ({@code roleAnonymous}, no name). The hash covers {@code payload + prevHash}
 * only, and the chain verifies before and after.
 */
public class StampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 8 November 2026, 20:25:25 and 400 ms in Malta (UTC+1). */
    static final Instant INSTANT = Instant.parse("2026-11-08T19:25:25.400Z");
    static final Clock CLOCK = Clock.fixed(INSTANT, MALTA);
    static final Date AT = Date.from(INSTANT);
    /** An instant a caller holds: 20:31:07 and 250 ms in Malta. */
    static final Instant HANDED = Instant.parse("2026-11-08T19:31:07.250Z");
    static final Date HANDED_AT = Date.from(HANDED);

    private FormDataDao dao;

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        when(dao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
    }

    private static RowStamp.Who who(final String user, final String name) {
        return new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
    }

    private static RowStamp.Who anna() {
        return who("anna.grech", "Anna Grech");
    }

    private static RowStamp.Who nobody() {
        return who("roleAnonymous", null);
    }

    private CaseEventWriter writer(RowStamp.Who who) {
        return new CaseEventWriter(dao, "statusEvent", CLOCK, who);
    }

    /** The rows the writer saved, in order. */
    private List<FormRow> saved(int n) {
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, times(n)).saveOrUpdate(eq("statusEvent"), eq("statusEvent"), cap.capture());
        List<FormRow> rows = new ArrayList<FormRow>();
        for (FormRowSet s : cap.getAllValues()) {
            assertEquals("one row in a save", 1, s.size());
            rows.add(s.get(0));
        }
        return rows;
    }

    private static void assertSixStamps(String what, FormRow r, Date at, String user, String name) {
        assertEquals(what + " dateCreated", at, r.getDateCreated());
        assertEquals(what + " dateModified", at, r.getDateModified());
        assertEquals(what + " createdBy", user, r.getCreatedBy());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " createdByName", name, r.getCreatedByName());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
    }

    /** A clock that counts how many times it is read. */
    private static final class CountingClock extends Clock {
        final AtomicInteger reads = new AtomicInteger();
        final Instant at;
        CountingClock(Instant at) { this.at = at; }
        @Override public ZoneId getZone() { return MALTA; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { reads.incrementAndGet(); return at; }
    }

    // -------------------------------------------------------- the six columns, person and nobody

    @Test
    public void anEventIsStampedAsANewRowByTheSignedInPerson() {
        writer(anna()).append("C1", "STATUS_CHANGED", "anna.grech", "open", "closed", "paid", null);
        FormRow r = saved(1).get(0);
        assertSixStamps("event", r, AT, "anna.grech", "Anna Grech");
        // the payload's moment and the row's eventTime are the same reading, in Malta's zone
        assertEquals("2026-11-08T20:25:25.400", r.getProperty("eventTime"));
        assertTrue(r.getProperty("payload").contains("\"timestamp\":\"2026-11-08T20:25:25.400\""));
    }

    @Test
    public void anEventWithNobodySignedInTakesThePlatformsOwnValueAndNoName() {
        writer(nobody()).append("C1", "STATUS_CHANGED", "system", "open", "closed", "sweep", null);
        FormRow r = saved(1).get(0);
        assertSixStamps("event", r, AT, "roleAnonymous", null);
        // the event's own actor column is what the caller passed, not the stamp's user
        assertEquals("system", r.getProperty("actor"));
    }

    @Test
    public void whereThePlatformsUserCannotBeReadTheStampLeavesTheWhoColumnsEmptyAndTheEventStands() {
        // the default constructor: the platform's own reader, with no application context in a unit test
        new CaseEventWriter(dao, "statusEvent").append("C1", "OPEN", "a", "", "OPEN", "r", null);
        FormRow r = saved(1).get(0);
        assertNotNull("the dates are still stamped", r.getDateCreated());
        assertNotNull(r.getDateModified());
        assertNull(r.getCreatedBy());
        assertNull(r.getModifiedBy());
        assertNull(r.getModifiedByName());
        assertNotNull("the event is written", r.getProperty("hash"));
    }

    // -------------------------------------------------------------------- one instant per event

    @Test
    public void aCallerThatHoldsTheMovesInstantHandsItAndTheEventIsThatReading() {
        CaseEventWriter w = writer(anna());
        w.atMoment(HANDED);
        w.append("C1", "STATUS_CHANGED", "anna.grech", "open", "closed", "paid", null);
        FormRow r = saved(1).get(0);
        assertSixStamps("event", r, HANDED_AT, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T20:31:07.250", r.getProperty("eventTime"));
        assertTrue(r.getProperty("payload").contains("\"timestamp\":\"2026-11-08T20:31:07.250\""));
    }

    @Test
    public void aCallerThatHoldsNoInstantLeavesTheWriterToReadItsClockOnce() {
        CountingClock clock = new CountingClock(INSTANT);
        new CaseEventWriter(dao, "statusEvent", clock, anna())
                .append("C1", "OPEN", "a", "", "OPEN", "r", null);
        assertEquals("the clock is read once for the event", 1, clock.reads.get());
        assertSixStamps("event", saved(1).get(0), AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aHandedInstantIsNotReadFromTheClockAtAll() {
        CountingClock clock = new CountingClock(INSTANT);
        CaseEventWriter w = new CaseEventWriter(dao, "statusEvent", clock, anna());
        w.atMoment(HANDED);
        w.append("C1", "OPEN", "a", "", "OPEN", "r", null);
        assertEquals("a handed instant is the reading; the clock is not read", 0, clock.reads.get());
    }

    @Test
    public void aHandedInstantServesTheNextAppendOnly() {
        CaseEventWriter w = writer(anna());
        w.atMoment(HANDED);
        w.append("C1", "OPEN", "a", "", "OPEN", "r0", null);
        w.append("C1", "MOVE", "a", "OPEN", "REVIEW", "r1", null);
        List<FormRow> rows = saved(2);
        assertSixStamps("first", rows.get(0), HANDED_AT, "anna.grech", "Anna Grech");
        assertSixStamps("second", rows.get(1), AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void aHandedInstantIsClearedEvenWhenTheAppendThrows() {
        when(dao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("store down"));
        CaseEventWriter w = writer(anna());
        w.atMoment(HANDED);
        try {
            w.append("C1", "OPEN", "a", "", "OPEN", "r", null);
            fail("the store error must reach the caller");
        } catch (IllegalStateException expected) {
            // fall through: the next event must not take the stale instant
        }
        doReturn(new FormRowSet()).when(dao).find(anyString(), anyString(), anyString(), any(),
                anyString(), any(), anyInt(), anyInt());
        w.append("C2", "OPEN", "a", "", "OPEN", "r", null);
        assertSixStamps("later event", saved(1).get(0), AT, "anna.grech", "Anna Grech");
    }

    @Test
    public void thePayloadMomentFollowsTheClocksZoneNotAnotherOne() {
        // the same instant read in a clock whose zone is UTC: 19:25:25.400, not Malta's 20:25:25.400
        new CaseEventWriter(dao, "statusEvent", Clock.fixed(INSTANT, ZoneOffset.UTC), anna())
                .append("C1", "OPEN", "a", "", "OPEN", "r", null);
        assertEquals("2026-11-08T19:25:25.400", saved(1).get(0).getProperty("eventTime"));
    }

    // ----------------------------------------------------------------------- the chain stands

    @Test
    public void theHashCoversThePayloadAndThePrevHashAndNotTheStamps() {
        // the same event written by two different people at the same instant: stamps differ, hash does not
        writer(anna()).append("C1", "STATUS_CHANGED", "x", "open", "closed", "paid", null);
        CaseEventWriter other = new CaseEventWriter(dao, "statusEvent", CLOCK, who("bob.vella", "Bob Vella"));
        other.append("C1", "STATUS_CHANGED", "x", "open", "closed", "paid", null);
        List<FormRow> rows = saved(2);
        assertEquals("anna.grech", rows.get(0).getModifiedBy());
        assertEquals("bob.vella", rows.get(1).getModifiedBy());
        assertEquals(rows.get(0).getProperty("hash"), rows.get(1).getProperty("hash"));
        for (FormRow r : rows) {
            assertEquals(CaseEventWriter.sha256(r.getProperty("payload") + r.getProperty("prevHash")),
                    r.getProperty("hash"));
        }
    }

    private ChainVerifyService verifierOver(List<FormRow> rows) {
        FormDataDao vdao = mock(FormDataDao.class);
        FormRowSet s = new FormRowSet();
        s.addAll(rows);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(s);
        return new ChainVerifyService(vdao, "statusEvent", "case");
    }

    private List<FormRow> chainOfThree() {
        CaseEventWriter w = writer(anna());
        w.append("C1", "OPEN", "a", "", "OPEN", "created", null);
        w.atMoment(HANDED);
        w.append("C1", "MOVE", "a", "OPEN", "REVIEW", "to review", "\"entity\":\"dcase\"");
        w.append("C1", "MOVE", "a", "REVIEW", "CLOSED", "done", null);
        return saved(3);
    }

    @Test
    public void aChainOfStampedEventsVerifies() {
        List<FormRow> rows = chainOfThree();
        for (FormRow r : rows) {
            assertNotNull("every event is stamped", r.getDateCreated());
            assertEquals("anna.grech", r.getCreatedBy());
        }
        ChainVerifyService.Result r = verifierOver(rows).verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(3, r.events);
    }

    /** A row as 2.0.0 wrote it: the hash chain and no stamp. */
    private static FormRow unstamped(long seq, String payload, String prevHash) {
        FormRow r = new FormRow();
        r.setProperty("seq", String.format("%010d", seq));
        r.setProperty("payload", payload);
        r.setProperty("prevHash", prevHash);
        r.setProperty("hash", CaseEventWriter.sha256(payload + prevHash));
        return r;
    }

    @Test
    public void aChainWrittenBeforeTheStampsVerifiesAsItDid() {
        FormRow g = unstamped(0, "{\"timestamp\":\"2026-08-01T10:00:00.000\",\"reason\":\"open\"}", "");
        FormRow e1 = unstamped(1, "{\"timestamp\":\"2026-08-01T10:05:00.000\",\"reason\":\"move\"}", g.getProperty("hash"));
        assertNull("written before: no stamp", g.getDateCreated());
        ChainVerifyService.Result r = verifierOver(java.util.Arrays.asList(g, e1)).verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(2, r.events);
    }

    @Test
    public void aChainOfEventsWrittenBeforeAndAfterTheStampsVerifiesAsOne() {
        FormRow g = unstamped(0, "{\"timestamp\":\"2026-08-01T10:00:00.000\",\"reason\":\"open\"}", "");
        // the writer, now stamping, continues the chain from the unstamped last row
        FormDataDao wdao = mock(FormDataDao.class);
        FormRowSet last = new FormRowSet();
        last.add(g);
        when(wdao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(last);
        new CaseEventWriter(wdao, "statusEvent", CLOCK, anna())
                .append("C1", "MOVE", "a", "OPEN", "REVIEW", "later", null);
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(wdao).saveOrUpdate(eq("statusEvent"), eq("statusEvent"), cap.capture());
        FormRow e1 = cap.getValue().get(0);
        assertEquals("0000000001", e1.getProperty("seq"));
        assertEquals(g.getProperty("hash"), e1.getProperty("prevHash"));
        assertSixStamps("new event", e1, AT, "anna.grech", "Anna Grech");
        ChainVerifyService.Result r = verifierOver(java.util.Arrays.asList(g, e1)).verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(2, r.events);
    }

    @Test
    public void aChangedStampDoesNotBreakTheChainButAChangedPayloadStillDoes() {
        List<FormRow> rows = chainOfThree();
        rows.get(1).setModifiedBy("someone.else");
        rows.get(1).setDateCreated(new Date(0));
        assertTrue("stamps are outside the hash", verifierOver(rows).verify("C1").ok);
        rows.get(1).setProperty("payload", "{\"reason\":\"TAMPERED\"}");
        ChainVerifyService.Result r = verifierOver(rows).verify("C1");
        assertFalse(r.ok);
        assertEquals(1, r.firstBadSeq);
        assertTrue(r.reason.contains("tampered"));
    }
}
