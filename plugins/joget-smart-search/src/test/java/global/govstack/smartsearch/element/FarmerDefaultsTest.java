package global.govstack.smartsearch.element;

import org.joget.apps.form.model.FormData;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

/**
 * The farmer search keeps working exactly as it did: an element that names no record draws the
 * farmer search with the data model it drew before this round, key for key and value for value,
 * both as its defaults give it and as a farmer form configures it. The expected values are those
 * the element as found puts together (its renderTemplate, read line by line); the same test run
 * on the element as found and after the change is the proof that nothing moved.
 */
public class FarmerDefaultsTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> renderModel(SmartSearchElement el) {
        Map model = new HashMap();
        try {
            el.renderTemplate(new FormData(), model);
        } catch (Throwable expectedOutsideThePlatform) {
            // FormUtil.generateElementHtml needs the platform's template engine
        }
        return new TreeMap<String, Object>(model);
    }

    private static Map<String, Object> farmerModel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldId", "farmerId");
        m.put("storeValue", "nationalId");
        m.put("value", "");
        m.put("displayMode", "popup");
        m.put("resourceBase",
                "/jw/web/json/plugin/global.govstack.smartsearch.element.SmartSearchResources/service?file=");
        m.put("apiEndpoint", "/jw/api/fss");
        m.put("apiId", "");
        m.put("apiKey", "");
        m.put("entityLabel", "farmer");
        m.put("simpleSearch", "false");
        m.put("populate", "");
        m.put("displayColumns", "nationalId,firstName,lastName,district,village");
        m.put("nationalIdPattern", "^\\d{9,13}$");
        m.put("nationalIdMinLength", 4);
        m.put("phonePattern", "^\\+?\\d{8,}$");
        m.put("phoneMinLength", 8);
        m.put("autoSelectSingleResult", false);
        m.put("autoSelectMinScore", 90);
        m.put("showAutoSelectNotification", false);
        m.put("showRecentFarmers", false);
        m.put("maxRecentFarmers", 5);
        return m;
    }

    @Test
    public void theFarmerSearchAsItsDefaultsGiveItIsUnchanged() {
        Map<String, Object> got = renderModel(new SmartSearchElement());
        Object elementId = got.remove("elementId");
        assertTrue(String.valueOf(elementId), String.valueOf(elementId).startsWith("fss_farmerId_"));
        assertEquals(new TreeMap<>(farmerModel()), got);
    }

    @Test
    public void aFarmerFormAsItIsConfiguredDrawsTheFarmerSearch() {
        SmartSearchElement el = new SmartSearchElement();
        el.setProperty("id", "farmer_ref");
        el.setProperty("storeValue", "id");
        el.setProperty("apiEndpoint", "/jw/api/fss");
        el.setProperty("apiId", "API-1");
        el.setProperty("displayColumns", "nationalId,firstName,lastName");
        el.setProperty("populate", "firstName:first_name,lastName:last_name");
        el.setProperty("entityLabel", "applicant");
        el.setProperty("simpleSearch", "true");
        el.setProperty("autoSelectSingleResult", "true");
        el.setProperty("showRecentFarmers", "true");
        el.setProperty("maxRecentFarmers", "7");
        Map<String, Object> got = renderModel(el);
        Object elementId = got.remove("elementId");
        assertTrue(String.valueOf(elementId), String.valueOf(elementId).startsWith("fss_farmer_ref_"));
        Map<String, Object> want = farmerModel();
        want.put("fieldId", "farmer_ref");
        want.put("storeValue", "id");
        want.put("apiId", "API-1");
        want.put("displayColumns", "nationalId,firstName,lastName");
        want.put("populate", "firstName:first_name,lastName:last_name");
        want.put("entityLabel", "applicant");
        want.put("simpleSearch", "true");
        want.put("autoSelectSingleResult", true);
        want.put("showRecentFarmers", true);
        want.put("maxRecentFarmers", 7);
        assertEquals(new TreeMap<>(want), got);
    }

    @Test
    public void aRequiredFarmerLeftUnchosenIsStillNamedAsAFarmer() {
        SmartSearchElement el = new SmartSearchElement();
        el.setProperty("id", "farmerId");
        el.setProperty("required", "true");
        FormData fd = new FormData();
        assertEquals(Boolean.FALSE, el.selfValidate(fd));
        assertEquals("Please select a farmer", fd.getFormError("farmerId"));
    }
}
