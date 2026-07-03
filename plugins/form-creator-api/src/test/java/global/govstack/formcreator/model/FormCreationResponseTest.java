package global.govstack.formcreator.model;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link FormCreationResponse} — the API's success/error envelope.
 * Pure (org.json only); no Joget runtime required.
 */
public class FormCreationResponseTest {

    @Test
    public void success_setsStatusFormIdMessage() {
        FormCreationResponse r = FormCreationResponse.success("myForm", "Form created successfully");

        assertEquals("success", r.getStatus());
        assertEquals("myForm", r.getFormId());
        assertEquals("Form created successfully", r.getMessage());
        assertNull("success carries no errorType", r.getErrorType());
        assertNull("success carries no errorMessage", r.getErrorMessage());
    }

    @Test
    public void error_setsStatusErrorTypeAndMessage() {
        FormCreationResponse r = FormCreationResponse.error("Validation Error", "formId is required");

        assertEquals("error", r.getStatus());
        assertEquals("Validation Error", r.getErrorType());
        assertEquals("formId is required", r.getErrorMessage());
        assertNull("error carries no formId", r.getFormId());
    }

    @Test
    public void constructor_stampsTimestamp() {
        FormCreationResponse r = new FormCreationResponse();
        assertNotNull("timestamp must be set at construction", r.getTimestamp());
        assertFalse(r.getTimestamp().trim().isEmpty());
    }

    @Test
    public void toJSON_omitsNullOptionalFields() {
        JSONObject json = FormCreationResponse.success("f1", "ok").toJSON();

        // present
        assertEquals("success", json.getString("status"));
        assertEquals("f1", json.getString("formId"));
        assertEquals("ok", json.getString("message"));
        assertTrue(json.has("timestamp"));
        // omitted because null
        assertFalse("apiId is null and must be omitted", json.has("apiId"));
        assertFalse("datalistId is null and must be omitted", json.has("datalistId"));
        assertFalse("userviewId is null and must be omitted", json.has("userviewId"));
        assertFalse("errorType is null and must be omitted", json.has("errorType"));
    }

    @Test
    public void toJSON_includesOptionalIdsWhenSet() {
        FormCreationResponse r = FormCreationResponse.success("f1", "ok");
        r.setApiId("API-123");
        r.setDatalistId("list_f1");
        r.setUserviewId("v");

        JSONObject json = r.toJSON();
        assertEquals("API-123", json.getString("apiId"));
        assertEquals("list_f1", json.getString("datalistId"));
        assertEquals("v", json.getString("userviewId"));
    }

    @Test
    public void toString_isValidJsonAndRoundTrips() {
        FormCreationResponse r = FormCreationResponse.error("Invalid JSON", "bad body");
        String s = r.toString();

        JSONObject parsed = new JSONObject(s); // must parse
        assertEquals("error", parsed.getString("status"));
        assertEquals("Invalid JSON", parsed.getString("errorType"));
        assertEquals("bad body", parsed.getString("errorMessage"));
    }
}
