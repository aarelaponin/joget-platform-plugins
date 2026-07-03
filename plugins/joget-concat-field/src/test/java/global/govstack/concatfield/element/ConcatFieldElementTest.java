package global.govstack.concatfield.element;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ConcatFieldElement}. Exercises the pure value-transform
 * helper (reached by reflection, since it is private) and the registration
 * identity that the platform registry and Activator depend on.
 */
public class ConcatFieldElementTest {

    private String transform(ConcatFieldElement el, String value, String kind) throws Exception {
        Method m = ConcatFieldElement.class.getDeclaredMethod("applyTransform", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(el, value, kind);
    }

    @Test
    public void applyTransform_casesAndTrim() throws Exception {
        ConcatFieldElement el = new ConcatFieldElement();
        assertEquals("ABC", transform(el, "abc", "uppercase"));
        assertEquals("abc", transform(el, "ABC", "lowercase"));
        assertEquals("hi", transform(el, "  hi  ", "trim"));
    }

    @Test
    public void applyTransform_capitalizeUpsFirstLowersRest() throws Exception {
        ConcatFieldElement el = new ConcatFieldElement();
        assertEquals("Hello", transform(el, "hELLO", "capitalize"));
        assertEquals("A", transform(el, "a", "capitalize"));
    }

    @Test
    public void applyTransform_nullValueBecomesEmpty() throws Exception {
        ConcatFieldElement el = new ConcatFieldElement();
        assertEquals("", transform(el, null, "uppercase"));
    }

    @Test
    public void applyTransform_nullOrEmptyTransformReturnsValueUnchanged() throws Exception {
        ConcatFieldElement el = new ConcatFieldElement();
        assertEquals("keep", transform(el, "keep", null));
        assertEquals("keep", transform(el, "keep", ""));
    }

    @Test
    public void applyTransform_unknownTransformPassesThrough() throws Exception {
        ConcatFieldElement el = new ConcatFieldElement();
        assertEquals("VaLuE", transform(el, "VaLuE", "rot13"));
    }

    @Test
    public void classNameMatchesRegisteredFqn() {
        // Guards the registry.yaml / Activator registration contract.
        assertEquals("global.govstack.concatfield.element.ConcatFieldElement",
                new ConcatFieldElement().getClassName());
    }
}
