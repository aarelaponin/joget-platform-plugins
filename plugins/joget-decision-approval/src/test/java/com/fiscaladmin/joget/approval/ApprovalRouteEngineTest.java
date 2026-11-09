package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * The approval level by amount (METHOD-2026-10-09-05, item 6; the debt management engagement's
 * BR-DM-037). A move that routes a record for approval sets the record's level attribute to the
 * level of the authority band ({@code mmAuthority}) its amount falls in, once the transition guard
 * has applied the move. The guard itself is stubbed here: it is another bundle's plugin, which the
 * engine runs by its class name.
 */
public class ApprovalRouteEngineTest {

    private static final String ENTITY = "write_off";
    private static final String TABLE = "mtcadm_write_off";
    private static final String ROUTES =
            "route_for_approval>awaiting_approval>total_amount>approval_required_from>write_off_approval";

    private FormDataDao dao;
    private FormRow row;
    private List<Map> guardCalls;

    private static FormRow band(String min, String max, String level) {
        FormRow r = new FormRow();
        r.setProperty("actionType", "write_off_approval");
        r.setProperty("amountMin", min);
        r.setProperty("amountMax", max);
        r.setProperty("level", level);
        return r;
    }

    private static FormRowSet set(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : rs) s.add(r);
        return s;
    }

    private Map<String, Object> props() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("entity", ENTITY);
        p.put("tableName", TABLE);
        p.put("statusField", "standing");
        p.put("actionField", "lc_action");
        p.put("transitions", "route_for_approval>awaiting_approval>>>|approve>approved>>>");
        p.put("routes", ROUTES);
        p.put("recordId", "WO1");
        return p;
    }

    /** The guard, as the platform's plugin would run it: applies the move asked for. */
    private ApprovalRouteEngine.Guard applies(final String to) {
        return (className, properties) -> {
            guardCalls.add(properties);
            row.setProperty("standing", to);
            row.setProperty("lc_action", "");
            row.setProperty("transition_result", "OK: " + to);
            return true;
        };
    }

    @Before
    public void setup() {
        dao = mock(FormDataDao.class);
        guardCalls = new ArrayList<Map>();
        row = new FormRow();
        row.setId("WO1");
        row.setProperty("standing", "requested");
        row.setProperty("lc_action", "route_for_approval");
        row.setProperty("total_amount", "10427.10");
        when(dao.load(ENTITY, TABLE, "WO1")).thenReturn(row);
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(),
                any(), any(), any(), any()))
                .thenReturn(set(band("0", "1000.00", "officer"),
                        band("1000.01", "20000.00", "senior_officer"),
                        band("20000.01", "", "director")));
    }

    private String route(String amount) {
        row.setProperty("standing", "requested");
        row.setProperty("lc_action", "route_for_approval");
        row.setProperty("total_amount", amount);
        return new ApprovalRouteEngine().run(props(), dao, applies("awaiting_approval"));
    }

    @Test
    public void aRoutedWriteOffGetsTheLevelItsTotalCallsFor() {
        String result = route("10427.10");
        assertEquals("senior_officer", row.getProperty("approval_required_from"));
        assertTrue(result, result.contains("approval level: senior_officer"));
        assertTrue(row.getProperty("transition_result").contains("approval level: senior_officer"));
        verify(dao, atLeastOnce()).saveOrUpdate(eq(ENTITY), eq(TABLE), any(FormRowSet.class));
    }

    @Test
    public void eachTotalGetsTheLevelOfItsBand() {
        route("850.00");
        assertEquals("officer", row.getProperty("approval_required_from"));
        route("1000.00");
        assertEquals("officer", row.getProperty("approval_required_from"));
        route("1000.01");
        assertEquals("senior_officer", row.getProperty("approval_required_from"));
        route("24650.00");
        assertEquals("director", row.getProperty("approval_required_from"));
    }

    @Test
    public void theGuardIsRunWithTheSamePropertiesAndTheEnginesOneReadingAsMoveMoment() {
        Map<String, Object> p = props();
        Map<String, Object> given = new HashMap<String, Object>(p);
        Clock fixed = Clock.fixed(Instant.parse("2026-11-09T06:15:30.250Z"), ZoneId.of("Europe/Malta"));
        new ApprovalRouteEngine(new RowStamps(fixed, new RowStamps.Who() {
            @Override public String username() { return "roleAnonymous"; }
            @Override public String fullName() { return null; }
        })).run(p, dao, applies("awaiting_approval"));
        assertEquals(1, guardCalls.size());
        Map<Object, Object> handed = new HashMap<Object, Object>(guardCalls.get(0));
        assertEquals("the engine's one reading, as an instant", "2026-11-09T06:15:30.250Z", handed.remove("moveMoment"));
        assertEquals("every property it was given, as it was given", new HashMap<Object, Object>(given), handed);
        assertEquals("the caller's own map is left as it was", given, p);
    }

    @Test
    public void aMoveTheGuardRefusesSetsNoLevel() {
        row.setProperty("total_amount", "10427.10");
        new ApprovalRouteEngine().run(props(), dao, (className, properties) -> {
            row.setProperty("lc_action", "");
            row.setProperty("transition_result", "REJECTED: guard failed");
            return true;                                  // ran, and left the record requested
        });
        assertNull(row.getProperty("approval_required_from"));
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
    }

    @Test
    public void anotherMoveSetsNoLevel() {
        row.setProperty("standing", "awaiting_approval");
        row.setProperty("lc_action", "approve");
        new ApprovalRouteEngine().run(props(), dao, applies("approved"));
        assertNull(row.getProperty("approval_required_from"));
        verify(dao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
    }

    @Test
    public void aTotalNoBandCoversIsSaidAndNoLevelIsSet() {
        when(dao.find(eq("mmAuthority"), eq("mmAuthority"), anyString(), any(),
                any(), any(), any(), any())).thenReturn(new FormRowSet());
        String result = route("10427.10");
        assertNull(row.getProperty("approval_required_from"));
        assertTrue(result, result.contains("approval level NOT set"));
        assertTrue(row.getProperty("transition_result").contains("approval level NOT set"));
    }

    @Test
    public void aTotalThatIsNoAmountIsSaidAndNoLevelIsSet() {
        String result = route("");
        assertNull(row.getProperty("approval_required_from"));
        assertTrue(result, result.contains("approval level NOT set"));
    }

    @Test
    public void aGuardNotInstalledIsSaidAndNothingMoves() {
        String result = new ApprovalRouteEngine().run(props(), dao, (className, properties) -> false);
        assertTrue(result, result.startsWith("ERROR"));
        assertTrue(result, result.contains("com.fiscaladmin.joget.transitionguard.TransitionGuard"));
        assertNull(row.getProperty("approval_required_from"));
        assertEquals("requested", row.getProperty("standing"));
        assertEquals(result, row.getProperty("transition_result"));
    }

    @Test
    public void onATriggerFormTheRecordNamedIsLevelled() {
        FormRow act = new FormRow();
        act.setId("ACT1");
        act.setProperty("lc_action", "route_for_approval");
        act.setProperty("record_key", "WO-2026-000001");
        when(dao.load("actWriteOffRoute", "mtcadm_write_off_act", "ACT1")).thenReturn(act);
        when(dao.find(eq("frmWriteOff"), eq(TABLE), contains("write_off_reference"), any(),
                any(), any(), any(), any())).thenReturn(set(row));
        Map<String, Object> p = props();
        p.put("recordId", "ACT1");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "write_off_reference");
        p.put("targetForm", "frmWriteOff");
        p.put("actForm", "actWriteOffRoute");
        p.put("actTable", "mtcadm_write_off_act");
        new ApprovalRouteEngine().run(p, dao, (className, properties) -> {
            row.setProperty("standing", "awaiting_approval");
            act.setProperty("transition_result", "OK: awaiting_approval");
            return true;
        });
        assertEquals("senior_officer", row.getProperty("approval_required_from"));
        assertTrue(act.getProperty("transition_result").contains("approval level: senior_officer"));
        verify(dao).saveOrUpdate(eq("frmWriteOff"), eq(TABLE), any(FormRowSet.class));
        verify(dao).saveOrUpdate(eq("actWriteOffRoute"), eq("mtcadm_write_off_act"),
                any(FormRowSet.class));
    }

    @Test
    public void theActivatorRegistersTheEngine() {
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        org.osgi.framework.BundleContext ctx = mock(org.osgi.framework.BundleContext.class);
        new Activator().start(ctx);
        verify(ctx, atLeastOnce()).registerService(names.capture(), any(), any());
        assertTrue(names.getAllValues().contains(ApprovalRouteEngine.class.getName()));
    }
}
