package global.govstack.formcreator.util;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ErrorResponseUtil} — the standardized error-JSON factory.
 * Asserts each helper stamps the correct errorType and always yields status=error.
 */
public class ErrorResponseUtilTest {

    private JSONObject parse(String s) {
        return new JSONObject(s);
    }

    @Test
    public void validationError_hasValidationErrorType() {
        JSONObject j = parse(ErrorResponseUtil.createValidationError("formId is required"));
        assertEquals("error", j.getString("status"));
        assertEquals("Validation Error", j.getString("errorType"));
        assertEquals("formId is required", j.getString("errorMessage"));
    }

    @Test
    public void internalServerError_hasInternalServerErrorType() {
        JSONObject j = parse(ErrorResponseUtil.createInternalServerError("boom"));
        assertEquals("error", j.getString("status"));
        assertEquals("Internal Server Error", j.getString("errorType"));
        assertEquals("boom", j.getString("errorMessage"));
    }

    @Test
    public void formCreationError_hasFormCreationErrorType() {
        JSONObject j = parse(ErrorResponseUtil.createFormCreationError("registration returned false"));
        assertEquals("Form Creation Error", j.getString("errorType"));
    }

    @Test
    public void invalidJsonError_hasInvalidJsonType() {
        JSONObject j = parse(ErrorResponseUtil.createInvalidJsonError("unexpected char"));
        assertEquals("Invalid JSON", j.getString("errorType"));
    }

    @Test
    public void appNotFoundError_embedsAppIdInMessage() {
        JSONObject j = parse(ErrorResponseUtil.createAppNotFoundError("platformRef"));
        assertEquals("Application Not Found", j.getString("errorType"));
        assertTrue("message should name the missing app",
                j.getString("errorMessage").contains("platformRef"));
    }

    @Test
    public void createErrorResponse_alwaysCarriesTimestampAndNoFormId() {
        JSONObject j = parse(ErrorResponseUtil.createErrorResponse("X", "y"));
        assertTrue(j.has("timestamp"));
        assertFalse("an error response has no formId", j.has("formId"));
    }
}
