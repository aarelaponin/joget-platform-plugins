package global.govstack.smartsearch.element;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.Test;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The record search's template drawn by FreeMarker, as the platform draws it (FormUtil puts the
 * element, its parameter name and the request beside the element's own data model): a template
 * that does not parse would show only on a running server. The page drawn here is also written to
 * target/record-search-page.html, for a browser to be pointed at.
 */
public class RecordSearchTemplateTest {

    /** The request, as far as the template touches it. */
    public static final class Request {
        private final Map<String, Object> attributes = new HashMap<>();

        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }

    private static String draw(List<String> problems, String value) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setDirectoryForTemplateLoading(new File("src/main/resources/templates"));
        cfg.setDefaultEncoding("UTF-8");
        Template t = cfg.getTemplate("RecordSearchElement.ftl");
        Map<String, Object> m = new HashMap<>();
        m.put("element", Collections.singletonMap("properties", Collections.singletonMap("label", "Holder")));
        m.put("elementParamName", "holder_code");
        m.put("elementMetaData", "");
        m.put("request", new Request());
        m.put("recordMode", Boolean.TRUE);
        m.put("fieldId", "holder_code");
        m.put("value", value);
        m.put("elementId", "rss_holder_code_1");
        m.put("resourceBase", "/jw/web/json/plugin/global.govstack.smartsearch.element.SmartSearchResources/service?file=");
        m.put("problems", problems);
        m.put("config", "{\"elementId\":\"rss_holder_code_1\",\"serviceUrl\":\"/service\",\"entityLabel\":\"holder\","
                + "\"columns\":[\"Code\",\"Name\"],\"fills\":[\"holder_name\"],\"locks\":[\"holder_name\"],"
                + "\"problems\":[]}");
        m.put("token", "T'1");
        m.put("nonce", "N1");
        StringWriter out = new StringWriter();
        t.process(m, out);
        return out.toString();
    }

    @Test
    public void theTemplateDrawsTheSearchWithItsSettings() throws Exception {
        String html = draw(Collections.emptyList(), "");
        assertTrue(html, html.contains("<input type=\"hidden\" id=\"rss_holder_code_1_value\" name=\"holder_code\" value=\"\">"));
        assertTrue(html, html.contains("class=\"rss-term\""));
        assertTrue(html, html.contains("<button type=\"button\" class=\"rss-go\">Search</button>"));
        assertTrue(html, html.contains("var cfg = {\"elementId\":\"rss_holder_code_1\""));
        assertTrue(html, html.contains("cfg.token = 'T\\'1';"));                 // the token, as a string
        assertTrue(html, html.contains("record-search.js&v=8.2.0"));
        assertTrue(html, html.contains(".rss-locked"));
        Files.createDirectories(new File("target").toPath());
        Files.write(new File("target/record-search-page.html").toPath(), html.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void aChoiceAlreadyMadeIsShownAndTheSearchIsClosed() throws Exception {
        String html = draw(Collections.emptyList(), "A-1");
        assertTrue(html, html.contains("value=\"A-1\""));
        assertTrue(html, html.contains("<div class=\"rss-find\" style=\"display:none;\">"));
        assertTrue(html, html.contains("<span class=\"rss-chosen-text\">A-1</span>"));
    }

    @Test
    public void settingsThatCannotSearchAreNamedAndNothingIsOffered() throws Exception {
        String html = draw(Arrays.asList("exactColumn 'c_code; DELETE' is not a plain column name"), "");
        assertTrue(html, html.contains("This search cannot search: exactColumn &#39;c_code; DELETE&#39; is not a plain column name."));
        assertFalse(html, html.contains("class=\"rss-term\""));
    }
}
