package global.govstack.smartsearch.element;

import org.joget.apps.form.model.FormData;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * The element set, from its own properties, to search a record the model names. These tests reach
 * the element through what it has always offered — its properties, its rendering's data model,
 * its validation and its properties page — so that they compile against the element as it was
 * found, and fail there by what it does.
 */
public class RecordModeElementTest {

    private static Map<String, String> column(String column, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("column", column);
        m.put("label", label);
        return m;
    }

    private static SmartSearchElement recordElement() {
        SmartSearchElement el = new SmartSearchElement();
        el.setProperty("id", "holder_code");
        el.setProperty("label", "Holder");
        el.setProperty("recordTable", "app_fd_holder");
        el.setProperty("exactColumn", "c_code");
        el.setProperty("fragmentColumn", "c_name");
        el.setProperty("storeValue", "c_code");
        el.setProperty("resultColumns", new Object[]{column("c_code", "Code"), column("c_name", "Name"),
                column("c_kind", "Kind")});
        el.setProperty("populate", "c_name:holder_name,c_kind:holder_kind");
        el.setProperty("locks", "holder_name,holder_kind");
        el.setProperty("entityLabel", "holder");
        el.setProperty("notFound", "No holder is registered under {searched}.");
        return el;
    }

    /** The data model the element put together before it asked the platform to draw it. Outside a
     *  running platform the drawing itself fails; the model it was given is what is read. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map renderModel(SmartSearchElement el) {
        Map model = new HashMap();
        try {
            el.renderTemplate(new FormData(), model);
        } catch (Throwable expectedOutsideThePlatform) {
            // FormUtil.generateElementHtml needs the platform's template engine
        }
        return model;
    }

    @Test
    public void theElementDrawsTheSearchOfTheRecordItsPropertiesName() {
        Map<?, ?> model = renderModel(recordElement());
        assertEquals(Boolean.TRUE, model.get("recordMode"));
        assertEquals("holder_code", model.get("fieldId"));
        assertEquals(Arrays.asList("Code", "Name", "Kind"), model.get("columns"));   // the result's columns
        assertEquals(Arrays.asList("holder_name", "holder_kind"), model.get("fills")); // what a choice fills
        assertEquals(Arrays.asList("holder_name", "holder_kind"), model.get("locks")); // and locks
        assertEquals("holder", model.get("entityLabel"));
        assertTrue(String.valueOf(model.get("serviceUrl")).endsWith(
                "/global.govstack.smartsearch.element.RecordSearchWebService/service"));
        assertTrue(String.valueOf(model.get("problems")), ((List<?>) model.get("problems")).isEmpty());
        // the page's script is given the same, as one object
        String config = String.valueOf(model.get("config"));
        assertTrue(config, config.contains("\"columns\":[\"Code\",\"Name\",\"Kind\"]"));
        assertTrue(config, config.contains("\"locks\":[\"holder_name\",\"holder_kind\"]"));
        // nothing of the farmer search is drawn for a record
        assertNull(model.get("apiEndpoint"));
        assertNull(model.get("nationalIdPattern"));
    }

    @Test
    public void aRecordSearchThatCannotSearchSaysWhyOnTheForm() {
        SmartSearchElement el = recordElement();
        el.setProperty("exactColumn", "c_code; DELETE");
        Map<?, ?> model = renderModel(el);
        assertEquals(Boolean.TRUE, model.get("recordMode"));
        List<?> problems = (List<?>) model.get("problems");
        assertFalse(problems.isEmpty());
        assertTrue(String.valueOf(problems), String.valueOf(problems).contains("exactColumn"));
    }

    @Test
    public void aRequiredChoiceLeftUnmadeIsNamedInTheRecordsWords() {
        SmartSearchElement el = recordElement();
        el.setProperty("required", "true");
        FormData fd = new FormData();
        assertEquals(Boolean.FALSE, el.selfValidate(fd));
        assertEquals("Choose the holder.", fd.getFormError("holder_code"));
    }

    @Test
    public void thePropertiesPageDeclaresEachRecordSetting() throws Exception {
        String json;
        try (InputStream in = SmartSearchElement.class.getResourceAsStream("/properties/SmartSearchElement.json")) {
            assertNotNull(in);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // read by pattern: the platform's own org.json classes, first on the test's class path, do
        // not parse with the org.json of the build
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            names.add(m.group(1));
        }
        for (String key : Arrays.asList("recordTable", "exactColumn", "fragmentColumn", "resultColumns",
                "populate", "locks", "notFound", "entityLabel")) {
            assertTrue(key + " is not on the element's properties page: " + names, names.contains(key));
        }
    }
}
