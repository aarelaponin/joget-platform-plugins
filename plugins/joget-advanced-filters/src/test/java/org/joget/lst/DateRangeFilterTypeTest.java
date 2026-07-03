package org.joget.lst;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for the private HTML/JS escapers in {@link DateRangeFilterType}.
 * These guard against markup/script injection through filter labels rendered
 * into the panel template, so they are worth pinning even though private.
 */
public class DateRangeFilterTypeTest {

    private String esc(String method, String in) throws Exception {
        Method m = DateRangeFilterType.class.getDeclaredMethod(method, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, in);
    }

    @Test
    public void escHtml_escapesMarkupChars() throws Exception {
        assertEquals("&lt;b&gt;", esc("escHtml", "<b>"));
        assertEquals("&quot;x&quot;", esc("escHtml", "\"x\""));
        // ampersand escaped first so entities are not double-encoded
        assertEquals("a &amp; b", esc("escHtml", "a & b"));
    }

    @Test
    public void escHtml_nullBecomesEmpty() throws Exception {
        assertEquals("", esc("escHtml", null));
    }

    @Test
    public void escJs_escapesQuotesBackslashNewline() throws Exception {
        assertEquals("it\\'s", esc("escJs", "it's"));
        assertEquals("a\\\\b", esc("escJs", "a\\b"));
        assertEquals("line1\\nline2", esc("escJs", "line1\nline2"));
    }

    @Test
    public void escJs_nullBecomesEmpty() throws Exception {
        assertEquals("", esc("escJs", null));
    }
}
