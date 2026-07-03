package global.govstack.formcreator.util;

import global.govstack.formcreator.exception.ValidationException;
import global.govstack.formcreator.model.FormCreationRequest;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link RequestParserUtil#parseJsonRequest(String)} and the
 * form-definition-presence guard. Exercises the JSON→request mapping, the
 * multi-format boolean coercion, and the empty/invalid-body error paths.
 */
public class RequestParserUtilTest {

    @Test
    public void parseJsonRequest_mapsRequiredFields() {
        String body = "{\"formId\":\"f1\",\"formName\":\"Form One\","
                + "\"tableName\":\"t1\",\"formDefinition\":\"{}\"}";

        FormCreationRequest r = RequestParserUtil.parseJsonRequest(body);

        assertEquals("f1", r.getFormId());
        assertEquals("Form One", r.getFormName());
        assertEquals("t1", r.getTableName());
        assertEquals("{}", r.getFormDefinitionJson());
    }

    @Test
    public void parseJsonRequest_mapsOptionalAppAndFlagFields() {
        String body = "{\"formId\":\"f1\",\"formName\":\"F\",\"tableName\":\"t\","
                + "\"formDefinition\":\"{}\",\"targetAppId\":\"appX\","
                + "\"createApiEndpoint\":true,\"createCrud\":\"yes\","
                + "\"apiName\":\"F API\",\"datalistName\":\"list_f\"}";

        FormCreationRequest r = RequestParserUtil.parseJsonRequest(body);

        assertEquals("appX", r.getTargetAppId());
        assertTrue("boolean true → createApiEndpoint", r.isCreateApiEndpoint());
        assertTrue("\"yes\" → createCrud", r.isCreateCrud());
        assertEquals("F API", r.getApiName());
        assertEquals("list_f", r.getDatalistName());
    }

    @Test
    public void parseJsonRequest_coercesNumericAndStringBooleans() {
        // 1 (numeric) → true ; "false" → false
        String body = "{\"formId\":\"f\",\"formName\":\"F\",\"tableName\":\"t\","
                + "\"formDefinition\":\"{}\",\"createApiEndpoint\":1,\"createCrud\":\"false\"}";

        FormCreationRequest r = RequestParserUtil.parseJsonRequest(body);

        assertTrue("numeric 1 → true", r.isCreateApiEndpoint());
        assertFalse("\"false\" → false", r.isCreateCrud());
    }

    @Test(expected = ValidationException.class)
    public void parseJsonRequest_emptyBodyThrows() {
        RequestParserUtil.parseJsonRequest("   ");
    }

    @Test(expected = ValidationException.class)
    public void parseJsonRequest_nullBodyThrows() {
        RequestParserUtil.parseJsonRequest(null);
    }

    @Test(expected = ValidationException.class)
    public void parseJsonRequest_malformedJsonThrows() {
        RequestParserUtil.parseJsonRequest("{not valid json");
    }

    @Test
    public void validateFormDefinitionPresent_passesWithJson() {
        FormCreationRequest r = new FormCreationRequest();
        r.setFormDefinitionJson("{}");
        RequestParserUtil.validateFormDefinitionPresent(r); // must not throw
    }

    @Test(expected = ValidationException.class)
    public void validateFormDefinitionPresent_throwsWhenAbsent() {
        RequestParserUtil.validateFormDefinitionPresent(new FormCreationRequest());
    }
}
