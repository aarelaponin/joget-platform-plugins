package global.govstack.workflow.activator.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PluginResponse} — the registry-client result envelope.
 * Pure Java, zero framework dependencies.
 */
public class PluginResponseTest {

    @Test
    public void success_isSuccessfulWith200AndData() {
        PluginResponse r = PluginResponse.success("payload");

        assertTrue(r.isSuccess());
        assertEquals(200, r.getStatusCode());
        assertEquals("payload", r.getData());
        assertEquals("Success", r.getMessage());
    }

    @Test
    public void error_defaultsTo500WithNullData() {
        PluginResponse r = PluginResponse.error("something broke");

        assertFalse(r.isSuccess());
        assertEquals(500, r.getStatusCode());
        assertNull(r.getData());
        assertEquals("something broke", r.getMessage());
    }

    @Test
    public void error_withExplicitStatusCode() {
        PluginResponse r = PluginResponse.error("not found", 404);

        assertFalse(r.isSuccess());
        assertEquals(404, r.getStatusCode());
        assertEquals("not found", r.getMessage());
    }

    @Test
    public void toString_success_returnsRawData() {
        assertEquals("payload", PluginResponse.success("payload").toString());
    }

    @Test
    public void toString_error_prefixesWithError() {
        assertEquals("Error: bad", PluginResponse.error("bad").toString());
    }
}
