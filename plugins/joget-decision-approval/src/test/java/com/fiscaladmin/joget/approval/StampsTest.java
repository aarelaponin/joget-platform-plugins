package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
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
 * The rows the decision and approval service and its engines save carry the columns the platform
 * fills when it saves a form's row (METHOD-2026-11-05-45; MT-DM's hand-off of 8 November 2026,
 * item 46), by the rule of {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196):
 * {@code dateModified}, {@code modifiedBy} and {@code modifiedByName} always; {@code dateCreated},
 * {@code createdBy} and {@code createdByName} only where the row holds no {@code dateCreated}.
 * The clock is fixed at an instant that is 20:25:25 in Europe/Malta, and the platform's user is
 * passed in: a person, or nobody (the platform's {@code roleAnonymous}, and no name). Each save of
 * the bundle has a case here: the approval ({@code ApprovalService.save}, a row made and a row
 * changed), the decision ({@code DecisionService.save}), the route engine's three saves, and the
 * gate, sweep, delegate and authority-check engines' one each. FormDataDao and the event writer
 * are mocked.
 */
public class StampsTest {

    static final ZoneId MALTA = ZoneId.of("Europe/Malta");
    /** 8 November 2026, 20:25:25 and 400 ms in Malta (UTC+1). */
    static final Instant INSTANT = Instant.parse("2026-11-08T19:25:25.400Z");
    static final Clock CLOCK = Clock.fixed(INSTANT, MALTA);
    static final Date AT = Date.from(INSTANT);
    /** A row the seed loader made on 6 November, by admin. */
    static final Date SEEDED = Date.from(Instant.parse("2026-11-06T12:36:51Z"));
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 20, 25, 25);

    private FormDataDao dao;

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

    /** The row as the platform left it when the seed loader (admin) saved it on 6 November. */
    private static FormRow seeded(FormRow r) {
        r.setDateCreated(SEEDED);
        r.setCreatedBy("admin");
        r.setCreatedByName("Administrator");
        r.setDateModified(SEEDED);
        r.setModifiedBy("admin");
        r.setModifiedByName("Administrator");
        return r;
    }

    private static RowStamps stamps(final String user, final String name) {
        return new RowStamps(CLOCK, new RowStamps.Who() {
            @Override public String username() { return user; }
            @Override public String fullName() { return name; }
        });
    }

    private static RowStamps anna() {
        return stamps("anna.grech", "Anna Grech");
    }

    private static RowStamps nobody() {
        return stamps("roleAnonymous", null);
    }

    /** A row made: all six columns, from the one instant and the platform's user. */
    private static void assertMade(String what, FormRow r, String user, String name) {
        assertEquals(what + " dateCreated", AT, r.getDateCreated());
        assertEquals(what + " dateModified", AT, r.getDateModified());
        assertEquals(what + " createdBy", user, r.getCreatedBy());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " createdByName", name, r.getCreatedByName());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
    }

    /** A row changed: the modified three moved, the created three as the row was loaded. */
    private static void assertChanged(String what, FormRow r, String user, String name) {
        assertEquals(what + " dateModified", AT, r.getDateModified());
        assertEquals(what + " modifiedBy", user, r.getModifiedBy());
        assertEquals(what + " modifiedByName", name, r.getModifiedByName());
        assertEquals(what + " dateCreated kept", SEEDED, r.getDateCreated());
        assertEquals(what + " createdBy kept", "admin", r.getCreatedBy());
        assertEquals(what + " createdByName kept", "Administrator", r.getCreatedByName());
    }

    /** The one row written to {@code table}, or a failure naming how many were. */
    private FormRow written(String form, String table) {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq(form), eq(table), rows.capture());
        List<FormRowSet> all = rows.getAllValues();
        assertEquals("one save to " + table, 1, all.size());
        assertEquals("one row in it", 1, all.get(0).size());
        return all.get(0).get(0);
    }

    private static FormRow transition(String from, String to) {
        FormRow r = new FormRow();
        r.setProperty("fromStatus", from);
        r.setProperty("toStatus", to);
        return r;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        // StatusManager guard: DEFAULT scope has rules and permits Pending -> each terminal
        when(dao.count(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any()))
                .thenReturn(1L);
        when(dao.find(eq("mmEntityTransition"), eq("mmEntityTransition"), anyString(), any(),
                anyString(), any(), any(), any()))
                .thenReturn(set(transition("Pending", "Approved"),
                        transition("Pending", "Rejected"),
                        transition("Pending", "Returned")));
        when(dao.find(eq("cmApproval"), eq("cmApproval"),
                contains("recordId"), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FormRowSet());
        when(dao.load(eq("cmCase"), eq("cmCase"), anyString())).thenReturn(null);
    }

    private ApprovalService service(RowStamps stamps) {
        return new ApprovalService(dao, mock(CaseEventWriter.class),
                new HashMap<String, DecisionEffect>(), stamps);
    }

    private FormRow pendingAp() {
        FormRow ap = seeded(row("AP-1", "status", "Pending", "entity", "dmWriteOff", "recordId", "W1",
                "actionType", "WRITE_OFF", "caseId", "C1", "requestedBy", "alice", "routeKind", "SINGLE",
                "requiredLevel", "MANAGER", "voters", "", "quorum", "1", "approvalsCount", "0",
                "deadline", "2026-11-01T10:00", "escalations", "0"));
        when(dao.load(eq("cmApproval"), eq("cmApproval"), eq("AP-1"))).thenReturn(ap);
        return ap;
    }

    // ---------------------------------------------------------- ApprovalService.save (:624)

    @Test
    public void aNewApprovalRequestIsStampedAsANewRow() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(), anyString(),
                any(), anyInt(), anyInt())).thenReturn(set(row("b1", "level", "MANAGER", "bodyType", "",
                "quorum", "", "amountMin", "0", "amountMax", "")));
        String r = service(anna()).request("dmWriteOff", "W1", "WRITE_OFF", 5000, "alice", "C1", "sys", NOW);
        assertTrue(r, r.startsWith("PENDING"));
        FormRow ap = written("cmApproval", "cmApproval");
        assertEquals("Pending", ap.getProperty("status"));
        assertMade("approval request", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void aDecidedApprovalIsStampedAsAChangedRow() {
        FormRow ap = pendingAp();
        assertEquals("REJECTED", service(anna()).decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertEquals("Rejected", ap.getProperty("status"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void aDecisionMadeWithNobodySignedInIsStampedWithThePlatformsOwnValue() {
        FormRow ap = pendingAp();
        service(nobody()).decide("AP-1", "bob", "DIRECTOR", "reject", "no", NOW);
        assertChanged("approval", ap, "roleAnonymous", null);
        assertEquals("admin", ap.getCreatedBy());
    }

    @Test
    public void anApprovalDelegatedIsStampedAsAChangedRow() {
        FormRow ap = pendingAp();
        assertEquals("DELEGATED to carla", service(anna()).delegate("AP-1", "bob", "carla", "away", NOW));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void anApprovalEscalatedBySweepIsStampedAsAChangedRow() {
        FormRow ap = pendingAp();
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        String r = service(anna()).sweep(NOW, "system");
        assertTrue(r, r.contains("escalated=1"));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    // --------------------------------------------------------- DecisionService.save (:147)

    @Test
    public void aDecisionRecordIsStampedAsAChangedRow() {
        FormRow d = seeded(row("D1", "actionType", "WRITE_OFF", "amount", "100", "approverLevel", "MANAGER",
                "reasons", "sound grounds", "caseId", "C1"));
        when(dao.load("cmDecision", "cmDecision", "D1")).thenReturn(d);
        String r = new DecisionService(dao, mock(CaseEventWriter.class), anna()).decide("D1", "bob", NOW);
        assertTrue(r, r.startsWith("APPROVED"));
        assertSame(d, written("cmDecision", "cmDecision"));
        assertChanged("decision", d, "anna.grech", "Anna Grech");
    }

    // ------------------------------------------------ ApprovalRouteEngine.save (:218, 4 calls)

    private static final String ENTITY = "write_off";
    private static final String TABLE = "mtcadm_write_off";
    private static final String ROUTES =
            "route_for_approval>awaiting_approval>total_amount>approval_required_from>write_off_approval";

    private FormRow record() {
        FormRow r = seeded(row("WO1", "standing", "requested", "lc_action", "route_for_approval",
                "total_amount", "10427.10"));
        when(dao.load(ENTITY, TABLE, "WO1")).thenReturn(r);
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(),
                any(), any(), any(), any()))
                .thenReturn(set(row("b1", "actionType", "write_off_approval", "amountMin", "0",
                        "amountMax", "20000.00", "level", "senior_officer")));
        return r;
    }

    private static Map<String, Object> routeProps() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("entity", ENTITY);
        p.put("tableName", TABLE);
        p.put("statusField", "standing");
        p.put("actionField", "lc_action");
        p.put("transitions", "route_for_approval>awaiting_approval>>>");
        p.put("routes", ROUTES);
        p.put("recordId", "WO1");
        return p;
    }

    @Test
    public void theRecordTheRouteLevelsIsStampedAsAChangedRow() {
        final FormRow r = record();
        new ApprovalRouteEngine(anna()).run(routeProps(), dao, (className, properties) -> {
            r.setProperty("standing", "awaiting_approval");
            r.setProperty("lc_action", "");
            r.setProperty("transition_result", "OK: awaiting_approval");
            return true;
        });
        assertEquals("senior_officer", r.getProperty("approval_required_from"));
        assertSame(r, written(ENTITY, TABLE));
        assertChanged("record", r, "anna.grech", "Anna Grech");
    }

    @Test
    public void onATriggerFormBothTheRecordLevelledAndTheActAreStampedAsChangedRows() {
        final FormRow r = record();
        final FormRow act = seeded(row("ACT1", "lc_action", "route_for_approval", "record_key", "WO-2026-000001"));
        when(dao.load("actWriteOffRoute", "mtcadm_write_off_act", "ACT1")).thenReturn(act);
        when(dao.find(eq("frmWriteOff"), eq(TABLE), contains("write_off_reference"), any(),
                any(), any(), any(), any())).thenReturn(set(r));
        Map<String, Object> p = routeProps();
        p.put("recordId", "ACT1");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "write_off_reference");
        p.put("targetForm", "frmWriteOff");
        p.put("actForm", "actWriteOffRoute");
        p.put("actTable", "mtcadm_write_off_act");
        new ApprovalRouteEngine(anna()).run(p, dao, (className, properties) -> {
            r.setProperty("standing", "awaiting_approval");
            act.setProperty("transition_result", "OK: awaiting_approval");
            return true;
        });
        assertSame(r, written("frmWriteOff", TABLE));
        assertChanged("record", r, "anna.grech", "Anna Grech");
        assertSame(act, written("actWriteOffRoute", "mtcadm_write_off_act"));
        assertChanged("act", act, "anna.grech", "Anna Grech");
    }

    @Test
    public void theResultSaidWhereTheGuardIsNotInstalledIsStampedToo() {
        FormRow r = record();
        String said = new ApprovalRouteEngine(anna()).run(routeProps(), dao, (className, properties) -> false);
        assertTrue(said, said.startsWith("ERROR"));
        assertSame(r, written(ENTITY, TABLE));
        assertChanged("record", r, "anna.grech", "Anna Grech");
    }

    @Test
    public void theRouteEngineWithNobodySignedInStampsThePlatformsOwnValue() {
        final FormRow r = record();
        new ApprovalRouteEngine(nobody()).run(routeProps(), dao, (className, properties) -> {
            r.setProperty("standing", "awaiting_approval");
            return true;
        });
        assertChanged("record", r, "roleAnonymous", null);
    }

    @Test
    public void aMoveTheGuardRefusedSavesNothingAndStampsNothing() {
        FormRow r = record();
        new ApprovalRouteEngine(anna()).run(routeProps(), dao, (className, properties) -> true);
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
        assertEquals(SEEDED, r.getDateModified());
    }

    // ------------------------------------------- the gate, sweep, delegate and check engines

    @Test
    public void theGateEnginesDecisionIsStampedAsAChangedRow() {
        FormRow dec = seeded(row("DEC1", "approvalId", "AP-9", "outcome", "approve", "reason", "sound",
                "approverLevel", "MANAGER"));
        when(dao.load("cmApprovalDecision", "cmApprovalDecision", "DEC1")).thenReturn(dec);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DEC1");
        new ApprovalGateEngine(anna()).run(p, dao, "bob");
        assertEquals("no approval AP-9", dec.getProperty("result"));
        assertSame(dec, written("cmApprovalDecision", "cmApprovalDecision"));
        assertChanged("decision", dec, "anna.grech", "Anna Grech");
    }

    @Test
    public void theSweepEnginesRunIsStampedAsAChangedRow() {
        FormRow run = seeded(row("SW1", "asOf", ""));
        when(dao.load("cmApprovalSweep", "cmApprovalSweep", "SW1")).thenReturn(run);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "SW1");
        new ApprovalSweepEngine(anna()).run(p, dao, "system");
        assertTrue(run.getProperty("result"), run.getProperty("result").startsWith("swept asOf"));
        assertSame(run, written("cmApprovalSweep", "cmApprovalSweep"));
        assertChanged("sweep", run, "anna.grech", "Anna Grech");
    }

    @Test
    public void theDelegateEnginesRecordIsStampedAsAChangedRow() {
        FormRow del = seeded(row("DL1", "approvalId", "AP-9", "delegateTo", "carla", "reason", "away"));
        when(dao.load("cmApprovalDelegate", "cmApprovalDelegate", "DL1")).thenReturn(del);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DL1");
        new ApprovalDelegateEngine(anna()).run(p, dao, "bob");
        assertEquals("no approval AP-9", del.getProperty("result"));
        assertSame(del, written("cmApprovalDelegate", "cmApprovalDelegate"));
        assertChanged("delegation", del, "anna.grech", "Anna Grech");
    }

    // the engines hand their own stamps to the service they run, so the approval they save follows the rule too

    @Test
    public void theApprovalTheGateEngineDecidesIsStampedWithTheEnginesStamps() {
        FormRow ap = pendingAp();
        FormRow dec = seeded(row("DEC1", "approvalId", "AP-1", "outcome", "reject", "reason", "no",
                "approverLevel", "DIRECTOR"));
        when(dao.load("cmApprovalDecision", "cmApprovalDecision", "DEC1")).thenReturn(dec);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DEC1");
        new ApprovalGateEngine(anna()).run(p, dao, "bob");
        assertEquals("REJECTED", dec.getProperty("result"));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void theApprovalTheSweepEngineEscalatesIsStampedWithTheEnginesStamps() {
        FormRow ap = pendingAp();
        when(dao.find(eq("cmApproval"), eq("cmApproval"), eq("WHERE e.customProperties.status = ?1"),
                any(), isNull(), any(), anyInt(), anyInt())).thenReturn(set(ap));
        FormRow run = seeded(row("SW1", "asOf", "2026-11-08T20:25:00"));
        when(dao.load("cmApprovalSweep", "cmApprovalSweep", "SW1")).thenReturn(run);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "SW1");
        new ApprovalSweepEngine(anna()).run(p, dao, "system");
        assertTrue(run.getProperty("result"), run.getProperty("result").contains("escalated=1"));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void theApprovalTheDelegateEngineHandsOnIsStampedWithTheEnginesStamps() {
        FormRow ap = pendingAp();
        FormRow del = seeded(row("DL1", "approvalId", "AP-1", "delegateTo", "carla", "reason", "away"));
        when(dao.load("cmApprovalDelegate", "cmApprovalDelegate", "DL1")).thenReturn(del);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "DL1");
        new ApprovalDelegateEngine(anna()).run(p, dao, "bob");
        assertEquals("DELEGATED to carla", del.getProperty("result"));
        assertSame(ap, written("cmApproval", "cmApproval"));
        assertChanged("approval", ap, "anna.grech", "Anna Grech");
    }

    @Test
    public void theAuthorityCheckIsStampedAsAChangedRow() {
        FormRow check = seeded(row("CK1", "actionType", "WRITE_OFF"));
        when(dao.load("cmAuthorityCheck", "cmAuthorityCheck", "CK1")).thenReturn(check);
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("recordId", "CK1");
        new AuthorityMatrixEngine(anna()).run(p, dao);
        assertNotNull(check.getProperty("result"));
        assertSame(check, written("cmAuthorityCheck", "cmAuthorityCheck"));
        assertChanged("check", check, "anna.grech", "Anna Grech");
    }

    // ----------------------------------------------------------------- the rule itself

    @Test
    public void aRowLoadedWithNoDateCreatedGetsItsCreatedThreeAsWellAsTheModifiedThree() {
        FormRow r = row("X1", "a", "b");
        r.setDateModified(SEEDED);
        r.setModifiedBy("admin");
        anna().stamp(r);
        assertMade("row", r, "anna.grech", "Anna Grech");
    }

    @Test
    public void aRowThatHoldsADateCreatedKeepsItsCreatedThreeWhateverTheyAre() {
        FormRow r = row("X1", "a", "b");
        r.setDateCreated(SEEDED);                       // no createdBy, no createdByName: kept as loaded
        anna().stamp(r);
        assertEquals(SEEDED, r.getDateCreated());
        assertNull(r.getCreatedBy());
        assertNull(r.getCreatedByName());
        assertEquals(AT, r.getDateModified());
        assertEquals("anna.grech", r.getModifiedBy());
    }

    @Test
    public void whereThePlatformsUserCannotBeReadTheStampLeavesTheWhoColumnsEmptyAndTheSaveStands() {
        // no application context in a unit test: the platform's own Who answers none
        assertNull(RowStamps.PLATFORM.username());
        assertNull(RowStamps.PLATFORM.fullName());
        FormRow r = row("X1", "a", "b");
        new RowStamps(CLOCK, RowStamps.PLATFORM).save(dao, "f", "t", r);
        assertSame(r, written("f", "t"));
        assertEquals(AT, r.getDateCreated());
        assertNull(r.getCreatedBy());
        assertNull(r.getModifiedByName());
    }
}
