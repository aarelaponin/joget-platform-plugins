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

/** LinkService — typed linkage + reciprocity + target validation, over a mocked FormDataDao. */
public class LinkServiceTest {

    private FormDataDao dao;
    private final Map<String, FormRow> links = new HashMap<String, FormRow>();
    private final Map<String, FormRow> cases = new HashMap<String, FormRow>();
    private final List<FormRow> linkTypes = new ArrayList<FormRow>();
    private final List<FormRow> events = new ArrayList<FormRow>();
    private LinkService svc;

    static FormRow row(String... kv) {
        FormRow r = new FormRow();
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    private static FormRowSet firstMatch(List<FormRow> pool, String field, Object val) {
        FormRowSet s = new FormRowSet();
        for (FormRow r : pool) {
            if (val.equals(CaseOpsSupport.prop(r, field))) { s.add(r); break; }
        }
        return s;
    }

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        when(dao.load(eq("caseLink"), eq("caseLink"), anyString())).thenAnswer(i -> links.get(i.getArguments()[2]));
        // link-type lookup (WHERE code = ?1)
        when(dao.find(eq("linkType"), eq("linkType"), anyString(), any(), anyString(), any(), any(), any()))
                .thenAnswer(i -> firstMatch(linkTypes, "code", ((Object[]) i.getArguments()[3])[0]));
        // case-by-ref lookup (WHERE caseRef = ?1)
        when(dao.find(eq("case"), eq("case"), anyString(), any(), anyString(), any(), any(), any()))
                .thenAnswer(i -> firstMatch(new ArrayList<FormRow>(cases.values()), "caseRef", ((Object[]) i.getArguments()[3])[0]));
        when(dao.find(eq("caseEvent"), eq("caseEvent"), anyString(), any(), anyString(), any(), any(), any()))
                .thenReturn(new FormRowSet());
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) links.put(r.getId(), r); return null; })
                .when(dao).saveOrUpdate(eq("caseLink"), eq("caseLink"), any(FormRowSet.class));
        doAnswer(i -> { for (FormRow r : (FormRowSet) i.getArguments()[2]) events.add(r); return null; })
                .when(dao).saveOrUpdate(eq("caseEvent"), eq("caseEvent"), any(FormRowSet.class));

        svc = new LinkService(dao, new CaseEventWriter(dao));
        linkTypes.add(row("code", "REFERRAL", "name", "Referral", "targetCaseTypes", "TEST"));
        linkTypes.add(row("code", "STRICT", "name", "Strict", "targetCaseTypes", "AUDIT"));
        FormRow target = row("caseType", "TEST", "caseRef", "TT-TO");
        target.setId("case-to");
        cases.put("case-to", target);
    }

    private long eventCount(String type) {
        return events.stream().filter(e -> type.equals(e.getProperty("eventType"))).count();
    }

    private FormRow seedLink(String id, String linkType, String toRef) {
        FormRow link = row("fromCaseId", "case-from", "fromCaseRef", "TT-FROM",
                "linkType", linkType, "toCaseRef", toRef, "result", "");
        link.setId(id);
        links.put(id, link);
        return link;
    }

    @Test
    public void permittedLinkWritesReciprocal() {
        FormRow link = seedLink("link-1", "REFERRAL", "TT-TO");
        String r = svc.link("link-1", "tester");
        assertTrue(r.contains("reciprocal"));
        assertEquals("case-to", link.getProperty("toCaseId"));
        assertEquals("true", link.getProperty("reciprocal"));
        assertEquals(2, links.size());
        assertEquals(2, eventCount("CASE_LINKED"));
    }

    @Test
    public void impermissibleTargetRejected() {
        seedLink("link-1", "STRICT", "TT-TO");
        String r = svc.link("link-1", "tester");
        assertTrue(r.startsWith("REJECTED"));
        assertEquals(1, links.size());
        assertEquals(0, eventCount("CASE_LINKED"));
    }

    @Test
    public void targetNotYetPresentLinksOneWay() {
        seedLink("link-1", "REFERRAL", "TT-UNKNOWN");
        String r = svc.link("link-1", "tester");
        assertTrue(r.contains("target not yet present"));
        assertEquals(1, links.size());
        assertEquals(1, eventCount("CASE_LINKED"));
    }

    @Test
    public void alreadyProcessedIsNoOp() {
        seedLink("link-1", "REFERRAL", "TT-TO");
        svc.link("link-1", "tester");
        int after = links.size();
        assertTrue(svc.link("link-1", "tester").contains("no-op"));
        assertEquals(after, links.size());
    }
}
