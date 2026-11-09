package com.fiscaladmin.joget.statusmanager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.eventchain.ChainVerifyService;
import com.fiscaladmin.joget.eventchain.RowStamp;
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
 * The status events {@link StatusManager} appends carry the platform's stamps from the move's
 * instant when the caller hands it (METHOD-2026-11-05-51, R34; MT-DM's hand-off of 8 November 2026,
 * answer to item 46), and from the writer's clock, read once, when it hands none. The event writer
 * is the real one over a mocked FormDataDao; the clock is fixed at an instant that is 20:25:25 in
 * Europe/Malta and the platform's user is passed in: a person, or nobody.
 */
public class StampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    static final Instant CLOCK_INSTANT = Instant.parse("2026-11-08T19:25:25.400Z");
    static final Clock CLOCK = Clock.fixed(CLOCK_INSTANT, MALTA);
    /** The move's instant: 20:31:07 and 250 ms in Malta. */
    static final Instant MOVE = Instant.parse("2026-11-08T19:31:07.250Z");

    private FormDataDao dao;
    private MmConfigService cfg;
    private FormRow record;

    private static RowStamp.Who who(final String user, final String name) {
        return new RowStamp.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        };
    }

    private static FormRow tr(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        record = new FormRow();
        record.setId("R1");
        record.setProperty("status", "DRAFT");
        when(dao.load("dmInst", "dmInst", "R1")).thenReturn(record);
        when(dao.find(eq("statusEvent"), eq("statusEvent"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(new FormRowSet());
        when(cfg.entityScopeHasRules("dmInst", "DEFAULT")).thenReturn(true);
        FormRowSet t = new FormRowSet();
        t.add(tr("DRAFT", "ACTIVE"));
        t.add(tr("ACTIVE", "CLOSED"));
        when(cfg.entityTransitions("dmInst", "DEFAULT")).thenReturn(t);
    }

    private StatusManager manager(RowStamp.Who who) {
        return new StatusManager(dao, cfg, new CaseEventWriter(dao, "statusEvent", CLOCK, who));
    }

    /** The one event row saved to the event carrier. */
    private FormRow event() {
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq("statusEvent"), eq("statusEvent"), cap.capture());
        assertEquals("one event", 1, cap.getAllValues().size());
        return cap.getValue().get(0);
    }

    private static void assertSixStamps(FormRow r, Instant at, String user, String name) {
        Date d = Date.from(at);
        assertEquals(d, r.getDateCreated());
        assertEquals(d, r.getDateModified());
        assertEquals(user, r.getCreatedBy());
        assertEquals(user, r.getModifiedBy());
        assertEquals(name, r.getCreatedByName());
        assertEquals(name, r.getModifiedByName());
    }

    @Test
    public void applyWithTheMovesInstantStampsTheEventFromThatInstantByThePerson() {
        manager(who("anna.grech", "Anna Grech")).apply("dmInst", record, "status", "C1", "ACTIVE",
                Collections.<String>emptyList(), "anna.grech", "activate", MOVE);
        FormRow e = event();
        assertSixStamps(e, MOVE, "anna.grech", "Anna Grech");
        assertEquals("2026-11-08T20:31:07.250", e.getProperty("eventTime"));
        assertEquals("STATUS_CHANGED", e.getProperty("eventType"));
        assertEquals("ACTIVE", record.getProperty("status"));
    }

    @Test
    public void applyWithTheMovesInstantAndNobodySignedInStampsThePlatformsOwnValue() {
        manager(who("roleAnonymous", null)).apply("dmInst", record, "status", "C1", "ACTIVE",
                Collections.<String>emptyList(), "system", "sweep", MOVE);
        assertSixStamps(event(), MOVE, "roleAnonymous", null);
    }

    @Test
    public void transitionWithTheMovesInstantStampsTheEventFromThatInstant() {
        manager(who("anna.grech", "Anna Grech")).transition("dmInst", "dmInst", "status", "R1", "C1",
                "ACTIVE", null, "anna.grech", "activate", MOVE);
        assertSixStamps(event(), MOVE, "anna.grech", "Anna Grech");
    }

    @Test
    public void applyWithoutAnInstantLeavesTheWriterToReadItsClockOnce() {
        manager(who("anna.grech", "Anna Grech")).apply("dmInst", record, "status", "C1", "ACTIVE",
                Collections.<String>emptyList(), "anna.grech", "activate");
        assertSixStamps(event(), CLOCK_INSTANT, "anna.grech", "Anna Grech");
    }

    @Test
    public void transitionWithoutAnInstantLeavesTheWriterToReadItsClockOnce() {
        manager(who("anna.grech", "Anna Grech")).transition("dmInst", "dmInst", "status", "R1", "C1",
                "ACTIVE", null, "anna.grech", "activate");
        assertSixStamps(event(), CLOCK_INSTANT, "anna.grech", "Anna Grech");
    }

    @Test
    public void anIllegalMoveWritesNoEventAndLeavesNoInstantBehindForTheNextMove() {
        StatusManager sm = manager(who("anna.grech", "Anna Grech"));
        try {
            sm.apply("dmInst", record, "status", "C1", "CLOSED", Collections.<String>emptyList(),
                    "anna.grech", "skip", MOVE);   // DRAFT -> CLOSED is not a transition
            fail("an illegal move is refused");
        } catch (InvalidTransitionException expected) {
            // refused before any event
        }
        verify(dao, never()).saveOrUpdate(eq("statusEvent"), anyString(), any(FormRowSet.class));
        // the next, legal move without an instant takes the clock's, not the refused move's
        sm.apply("dmInst", record, "status", "C1", "ACTIVE", Collections.<String>emptyList(),
                "anna.grech", "activate");
        assertSixStamps(event(), CLOCK_INSTANT, "anna.grech", "Anna Grech");
    }

    @Test
    public void theChainOfEventsAMoveWritesVerifies() {
        StatusManager sm = manager(who("anna.grech", "Anna Grech"));
        sm.apply("dmInst", record, "status", "C1", "ACTIVE", Collections.<String>emptyList(),
                "anna.grech", "activate", MOVE);
        sm.apply("dmInst", record, "status", "C1", "CLOSED", Collections.<String>emptyList(),
                "anna.grech", "close");
        ArgumentCaptor<FormRowSet> cap = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, times(2)).saveOrUpdate(eq("statusEvent"), eq("statusEvent"), cap.capture());
        FormRowSet chain = new FormRowSet();
        chain.add(cap.getAllValues().get(0).get(0));
        chain.add(cap.getAllValues().get(1).get(0));
        FormDataDao vdao = mock(FormDataDao.class);
        when(vdao.find(anyString(), anyString(), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(chain);
        ChainVerifyService.Result r = new ChainVerifyService(vdao, "statusEvent", "case").verify("C1");
        assertTrue(r.reason, r.ok);
        assertEquals(2, r.events);
    }
}
