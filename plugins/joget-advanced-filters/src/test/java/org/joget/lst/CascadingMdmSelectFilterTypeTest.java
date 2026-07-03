package org.joget.lst;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CascadingMdmSelectFilterType#sanitize} — the SQL-identifier
 * whitelist that keeps a filter's column name from carrying injection — plus the
 * shared HTML/JS escapers.
 */
public class CascadingMdmSelectFilterTypeTest {

    private String sanitize(String in) throws Exception {
        Method m = CascadingMdmSelectFilterType.class.getDeclaredMethod("sanitize", String.class);
        m.setAccessible(true);
        return (String) m.invoke(new CascadingMdmSelectFilterType(), in);
    }

    private String esc(String method, String in) throws Exception {
        Method m = CascadingMdmSelectFilterType.class.getDeclaredMethod(method, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, in);
    }

    @Test
    public void sanitize_keepsWordCharsAndUnderscore() throws Exception {
        assertEquals("c_district_code", sanitize("c_district_code"));
        assertEquals("Col123", sanitize("Col123"));
    }

    @Test
    public void sanitize_stripsInjectionPunctuation() throws Exception {
        // every char outside [a-zA-Z0-9_] (spaces, ; ' = - etc.) is removed
        assertEquals("c_codeDROPTABLEx", sanitize("c_code; DROP TABLE x--"));
        assertEquals("aOR11", sanitize("a' OR 1=1"));
    }

    @Test
    public void sanitize_removesWhitespaceAndSymbols() throws Exception {
        assertEquals("abc", sanitize(" a b\tc "));
        assertEquals("c_x", sanitize("c_x`"));
    }

    @Test
    public void escHtml_escapesMarkup() throws Exception {
        assertEquals("&lt;i&gt;", esc("escHtml", "<i>"));
        assertEquals("", esc("escHtml", null));
    }

    @Test
    public void escJs_escapesQuote() throws Exception {
        assertEquals("d\\'or", esc("escJs", "d'or"));
        assertEquals("", esc("escJs", null));
    }
}
