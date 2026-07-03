package global.govstack.lookupfield.element;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Identity/registration guards for {@link LookupFieldElement}. The className must
 * stay in lock-step with registry.yaml (registers:) and the Activator; a silent
 * rename there is exactly what the L2 manifest-smoke check cross-verifies at
 * build time, and this pins it at unit level too.
 */
public class LookupFieldElementTest {

    @Test
    public void classNameMatchesRegisteredFqn() {
        assertEquals("global.govstack.lookupfield.element.LookupFieldElement",
                new LookupFieldElement().getClassName());
    }

    @Test
    public void metadataIsPopulated() {
        LookupFieldElement el = new LookupFieldElement();
        assertNotNull(el.getName());
        assertFalse(el.getName().trim().isEmpty());
        assertNotNull(el.getVersion());
        assertNotNull(el.getLabel());
        assertTrue("form-builder position is non-negative", el.getFormBuilderPosition() >= 0);
    }
}
