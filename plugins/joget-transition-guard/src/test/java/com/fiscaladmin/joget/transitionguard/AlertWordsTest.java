package com.fiscaladmin.joget.transitionguard;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;
import com.fiscaladmin.joget.statusmanager.MmConfigService;
import com.fiscaladmin.joget.statusmanager.StatusManager;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * An alert that shows its template's words and points at its template (1.8.0; MT-DM DM3-2026-10-27-159). A column of
 * source {@code template_key} holds the identifier of the template row the notify names, read where {@code templates}
 * says ({@code form>table>nameColumn>contentColumn}); a column of source {@code content} holds that row's content, its
 * merge fields {@code {name}} filled from the move and the record moved, a field nothing fills left as written. The
 * source {@code template} still holds the template's name, as before. FormDataDao, MmConfigService and the event
 * writer are mocked; the state machine is real.
 */
public class AlertWordsTest {

    static final String TEMPLATES = "frmTemplate>patmin_template>name>content";
    static final String NOTIFY = "close>attr:holder>case_closed>alert>patmin_alert>"
            + "addressed_to=recipient,template=template_key,summary=content,named=template,about=attr:case_number";
    static final String REFUSED = "notify>close>attr:holder>case_refused>alert>patmin_alert>"
            + "addressed_to=recipient,template=template_key,summary=content";

    private FormDataDao dao;
    private MmConfigService cfg;
    private StatusManager sm;
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

    private static Map<String, String> trigger() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("entity", "dcase");
        p.put("tableName", "patmin_case");
        p.put("statusField", "state");
        p.put("actionField", "lc_action");
        p.put("recordId", "act-1");
        p.put("transitions", "close>closing>closed_on@date>amount eq 0>");
        p.put("targetField", "record_key");
        p.put("targetKeyColumn", "case_number");
        p.put("targetForm", "frmCase");
        p.put("actForm", "actDcaseClose");
        p.put("actTable", "patmin_case_act");
        p.put("reasonField", "reason");
        p.put("copyFields", "");
        p.put("templates", TEMPLATES);
        return p;
    }

    private void template(String name, String id, String content) {
        when(dao.find(eq("frmTemplate"), eq("patmin_template"), eq("WHERE e.customProperties.name = ?1"),
                aryEq(new Object[]{name}), isNull(), eq(Boolean.FALSE), eq(0), eq(2)))
                .thenReturn(set(row(id, "name", name, "content", content)));
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        cfg = mock(MmConfigService.class);
        sm = new StatusManager(dao, cfg, mock(CaseEventWriter.class));
        FormRow act = row("act-1", "lc_action", "close", "record_key", "DC-000001", "reason", "paid in full");
        record = row("case-uuid", "case_number", "DC-000001", "state", "open", "amount", "0",
                "holder", "carmen.abela", "taxpayer_name", "Zammit Construction Ltd");
        when(dao.load("actDcaseClose", "patmin_case_act", "act-1")).thenReturn(act);
        when(dao.find(eq("frmCase"), eq("patmin_case"),
                eq("WHERE e.customProperties.case_number = ?1"), any(Object[].class),
                isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(set(record));
        when(cfg.entityScopeHasRules("dcase", "DEFAULT")).thenReturn(true);
        when(cfg.entityTransitions("dcase", "DEFAULT")).thenReturn(set(
                row("m1", "fromStatus", "open", "toStatus", "closing", "transitionCode", "close")));
    }

    private FormRow alert() {
        ArgumentCaptor<FormRowSet> rows = ArgumentCaptor.forClass(FormRowSet.class);
        verify(dao, atLeastOnce()).saveOrUpdate(eq("alert"), eq("patmin_alert"), rows.capture());
        List<FormRowSet> all = rows.getAllValues();
        assertEquals("one alert", 1, all.size());
        return all.get(0).get(0);
    }

    @Test
    public void anAlertPointsAtItsTemplateAndShowsItsWordsFilled() {
        template("case_closed", "tpl-0007", "Case {case_number}, {taxpayer_name}, closed by {actor} for {recipient}: {reason}\n");
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY);
        String result = new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        assertEquals("OK: closing", result);
        FormRow a = alert();
        assertEquals("the template's key, not its name", "tpl-0007", a.getProperty("template"));
        assertEquals("Case DC-000001, Zammit Construction Ltd, closed by anna for carmen.abela: paid in full",
                a.getProperty("summary"));
        assertEquals("the source template keeps the name", "case_closed", a.getProperty("named"));
        assertEquals("carmen.abela", a.getProperty("addressed_to"));
    }

    @Test
    public void aMergeFieldNothingFillsIsLeftAsWritten() {
        template("case_closed", "tpl-0007", "{senior_officer_name}, case {case_number} is closed");
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY);
        new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        assertEquals("{senior_officer_name}, case DC-000001 is closed", alert().getProperty("summary"));
    }

    @Test
    public void aTemplateNotFoundWritesItsNameAndNoKeyAndSaysSo() {
        Map<String, String> p = trigger();
        p.put("notify", NOTIFY);
        String result = new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        FormRow a = alert();
        assertEquals("", a.getProperty("template"));
        assertEquals("case_closed", a.getProperty("summary"));
        assertTrue(result, result.startsWith("OK: closing") && result.contains("template 'case_closed' not found"));
    }

    @Test
    public void aRefusedMovesAlertFillsTheRefusalsWords() {
        template("case_refused", "tpl-0008", "{refusal_words}\nThe closure of {case_number} is refused.");
        record.setProperty("amount", "5");
        Map<String, String> p = trigger();
        p.put("refused", REFUSED);
        String result = new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        assertTrue(result, result.startsWith("REJECTED: "));
        FormRow a = alert();
        assertEquals("tpl-0008", a.getProperty("template"));
        String said = result.substring("REJECTED: ".length()).trim();
        assertEquals(said + "\nThe closure of DC-000001 is refused.", a.getProperty("summary"));
    }

    // ---- a template kept in versions (METHOD-2026-11-05-03 version 2): one row a version, as MT-DM's `template` keeps
    // it; `templates` names the two columns of its validity, and the version in effect on the move's day is read

    static final String VERSIONED = TEMPLATES + ">effective_from>effective_to";

    private static String day(int offset) {
        return java.time.LocalDate.now().plusDays(offset).toString();
    }

    private static FormRow version(String id, String from, String to, String content) {
        return row(id, "name", "case_closed", "effective_from", from, "effective_to", to, "content", content);
    }

    /** The rows of the name, as the dated read asks for them: every row, no limit. */
    private void versions(FormRow... rows) {
        when(dao.find(eq("frmTemplate"), eq("patmin_template"), eq("WHERE e.customProperties.name = ?1"),
                aryEq(new Object[]{"case_closed"}), isNull(), eq(Boolean.FALSE), eq(0), isNull()))
                .thenReturn(set(rows));
    }

    private String runVersioned(String templates) {
        Map<String, String> p = trigger();
        p.put("templates", templates);
        p.put("notify", NOTIFY);
        return new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
    }

    @Test
    public void aTemplateKeptInVersionsReadsTheVersionInEffect() {
        versions(version("tpl-v1", "2000-01-01", day(-30), "Version 1: case {case_number}"),
                version("tpl-v2", day(-29), "", "Version 2: case {case_number}"),
                version("tpl-v3", day(10), "", "Version 3: case {case_number}"));
        String result = runVersioned(VERSIONED);
        assertEquals("OK: closing", result);
        FormRow a = alert();
        assertEquals("tpl-v2", a.getProperty("template"));
        assertEquals("Version 2: case DC-000001", a.getProperty("summary"));
    }

    @Test
    public void ofTwoVersionsInEffectTheLaterStartIsRead() {
        versions(version("tpl-v1", "2000-01-01", "", "Version 1"),
                version("tpl-v2", day(-5) + " 00:00:00", "", "Version 2"));
        runVersioned(VERSIONED);
        FormRow a = alert();
        assertEquals("tpl-v2", a.getProperty("template"));
        assertEquals("Version 2", a.getProperty("summary"));
    }

    @Test
    public void aValidityWithoutAnEndColumnReadsNoEnd() {
        versions(version("tpl-v1", "2000-01-01", day(-30), "Version 1"),
                version("tpl-v2", day(-29), day(-1), "Version 2"));
        runVersioned(TEMPLATES + ">effective_from>");
        FormRow a = alert();
        assertEquals("the end is not read: the later start", "tpl-v2", a.getProperty("template"));
    }

    @Test
    public void noVersionInEffectWritesTheNameAndSaysSo() {
        versions(version("tpl-v1", "2000-01-01", day(-30), "Version 1"),
                version("tpl-v2", day(10), "", "Version 2"));
        String result = runVersioned(VERSIONED);
        FormRow a = alert();
        assertEquals("", a.getProperty("template"));
        assertEquals("case_closed", a.getProperty("summary"));
        assertTrue(result, result.startsWith("OK: closing")
                && result.contains("template 'case_closed' has no version in effect on " + day(0)));
    }

    @Test
    public void twoVersionsInEffectFromOneDayWriteTheNameAndSaySo() {
        versions(version("tpl-v1", day(-5), "", "Version 1"), version("tpl-v2", day(-5), "", "Version 2"));
        String result = runVersioned(VERSIONED);
        FormRow a = alert();
        assertEquals("", a.getProperty("template"));
        assertEquals("case_closed", a.getProperty("summary"));
        assertTrue(result, result.contains("template 'case_closed' has two versions in effect on " + day(0)
                + " from the same day"));
    }

    @Test
    public void aNameHeldByTwoRowsWithoutValiditySaysSo() {
        when(dao.find(eq("frmTemplate"), eq("patmin_template"), eq("WHERE e.customProperties.name = ?1"),
                aryEq(new Object[]{"case_closed"}), isNull(), eq(Boolean.FALSE), eq(0), eq(2)))
                .thenReturn(set(version("tpl-v1", "2000-01-01", "", "Version 1"),
                        version("tpl-v2", day(-5), "", "Version 2")));
        String result = runVersioned(TEMPLATES);
        FormRow a = alert();
        assertEquals("", a.getProperty("template"));
        assertEquals("case_closed", a.getProperty("summary"));
        assertTrue(result, result.contains("template 'case_closed' is held by more than one row"));
    }

    @Test
    public void noTemplatesPropertyReadsAsBefore() {
        Map<String, String> p = trigger();
        p.remove("templates");
        p.put("notify", "close>attr:holder>case_closed>alert>patmin_alert>summary=template");
        new TransitionGuard().run(p, dao, cfg, sm, u -> Arrays.asList("officer"), "anna");
        assertEquals("case_closed", alert().getProperty("summary"));
        verify(dao, never()).find(eq("frmTemplate"), anyString(), anyString(), any(Object[].class), any(), any(), any(), any());
    }
}
