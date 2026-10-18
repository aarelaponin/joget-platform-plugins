package com.fiscaladmin.joget.approval;

import java.util.Arrays;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * An approval act withheld from its requester (METHOD-2026-10-16-01, construct 2; MT-DM's request
 * of 15 October 2026, item 2).
 *
 * <p>RequesterWithheld is a permission on a form element. In {@code others}, the default, it shows
 * the element to a person who holds one of {@code allowedGroupIds} (all, where it names none) and
 * did not ask for the record, and hides it from the record's requester: the act bar of the record's
 * form holds the withheld move under it. In {@code requester} it shows the element to the record's
 * requester alone: the notice that stands in place of the move's button on the move's own form. The
 * record is read by the key the address carries ({@code keyParam}), by its row id or by
 * {@code keyColumn}; its requester is {@code requesterColumn}. The four-eyes rule of the decide path
 * (ApprovalService.decide, ApprovalInbox.eligible) is not touched, and its tests stand.
 */
public class RequesterWithheldTest {

    private static FormRow row(String id, String... kv) {
        FormRow r = new FormRow();
        r.setId(id);
        for (int i = 0; i < kv.length; i += 2) r.setProperty(kv[i], kv[i + 1]);
        return r;
    }

    @Test
    public void othersAreShownTheActAndTheRequesterIsNot() {
        assertTrue(RequesterWithheld.authorise("others", "anna.grech", "boris.vella", true));
        assertFalse(RequesterWithheld.authorise("others", "anna.grech", "anna.grech", true));
        assertFalse("case does not matter", RequesterWithheld.authorise("others", "anna.grech", "Anna.Grech", true));
        assertTrue("empty is the default", RequesterWithheld.authorise("", "anna.grech", "boris.vella", true));
    }

    @Test
    public void aPersonWithoutTheGroupsIsNotShownTheAct() {
        assertFalse(RequesterWithheld.authorise("others", "anna.grech", "boris.vella", false));
    }

    @Test
    public void aRecordNamingNobodyWithholdsTheActFromNobody() {
        assertTrue(RequesterWithheld.authorise("others", "", "boris.vella", true));
        assertFalse("no requester: no notice", RequesterWithheld.authorise("requester", "", "boris.vella", true));
    }

    @Test
    public void theNoticeIsShownToTheRequesterAlone() {
        assertTrue(RequesterWithheld.authorise("requester", "anna.grech", "anna.grech", true));
        assertFalse(RequesterWithheld.authorise("requester", "anna.grech", "boris.vella", true));
        assertFalse("nobody signed in", RequesterWithheld.authorise("requester", "", "", true));
    }

    /** 1.3.1: the class names jakarta.servlet, which Joget 9 serves, and no javax.servlet. */
    @Test
    public void theBytesNameJakartaServletAndNoJavaxServlet() throws Exception {
        java.io.InputStream in = RequesterWithheld.class.getResourceAsStream("RequesterWithheld.class");
        assertNotNull(in);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        in.close();
        String bytes = new String(out.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(bytes.contains("jakarta/servlet/http/HttpServletRequest"));
        assertFalse(bytes.contains("javax/servlet"));
        assertEquals("1.3.1", new RequesterWithheld().getVersion());
    }

    @Test
    public void theRequesterIsReadFromTheRecordByItsRowIdOrItsKey() {
        FormDataDao dao = mock(FormDataDao.class);
        when(dao.load("frmCase", "patmin_case", "case-uuid"))
                .thenReturn(row("case-uuid", "requested_by", "anna.grech"));
        FormRowSet one = new FormRowSet();
        one.addAll(Arrays.asList(row("case-uuid", "requested_by", "anna.grech")));
        when(dao.find(eq("frmCase"), eq("patmin_case"), eq("WHERE e.customProperties.case_number = ?1"),
                any(Object[].class), isNull(), eq(Boolean.FALSE), eq(0), eq(2))).thenReturn(one);
        assertEquals("anna.grech",
                RequesterWithheld.requester(dao, "frmCase", "patmin_case", "", "case-uuid", "requested_by"));
        assertEquals("anna.grech",
                RequesterWithheld.requester(dao, "frmCase", "patmin_case", "case_number", "DC-000001", "requested_by"));
        assertEquals("", RequesterWithheld.requester(dao, "frmCase", "patmin_case", "", "", "requested_by"));
        assertEquals("", RequesterWithheld.requester(dao, "frmCase", "patmin_case", "", "nope", "requested_by"));
        assertEquals("not a column", "",
                RequesterWithheld.requester(dao, "frmCase", "patmin_case", "a b", "DC-1", "requested_by"));
    }
}
