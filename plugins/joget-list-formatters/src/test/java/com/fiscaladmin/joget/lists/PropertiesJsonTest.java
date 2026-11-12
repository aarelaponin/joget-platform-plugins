package com.fiscaladmin.joget.lists;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The property editor's page names the four properties the formatter reads, with the binder chosen as the platform's own
 * formatter's page chooses it (/properties/datalist/optionsValueFormatter.json of jw-enterprise-plugins 9.0.7).
 */
public class PropertiesJsonTest {

    private static String page() throws Exception {
        try (InputStream in = WordedOptionsFormatter.class.getResourceAsStream("/properties/wordedOptionsFormatter.json")) {
            assertNotNull("the properties page is part of the bundle", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void thePageNamesTheFourPropertiesTheFormatterReads() throws Exception {
        String page = page();
        for (String name : new String[] { "optionsBinder", "options", "split", "unlisted" }) {
            assertTrue("property " + name, page.contains("\"name\": \"" + name + "\""));
        }
    }

    @Test
    public void theBinderIsChosenAsAFormLoadOptionsBinderAsThePlatformsFormatterChoosesIt() throws Exception {
        String page = page();
        assertTrue(page.contains("\"type\": \"elementselect\""));
        assertTrue(page.contains("getElements?classname=org.joget.apps.form.model.FormLoadOptionsBinder"));
    }

    @Test
    public void theGridHoldsAValueAndALabelAsThePlatformsGridDoes() throws Exception {
        String page = page();
        assertTrue(page.contains("\"key\": \"value\""));
        assertTrue(page.contains("\"key\": \"label\""));
    }

    @Test
    public void asStoredIsTheOnlyValueUnlistedTakes() throws Exception {
        String page = page();
        String tail = page.substring(page.indexOf("\"name\": \"unlisted\""));
        Set<String> values = new TreeSet<String>();
        Matcher m = Pattern.compile("\"value\": \"([^\"]*)\"").matcher(tail);
        while (m.find()) {
            values.add(m.group(1));
        }
        assertEquals("[as_stored]", values.toString());
    }

    @Test
    public void splitTakesTrueOrFalseAndDefaultsToTrue() throws Exception {
        String page = page();
        int from = page.indexOf("\"name\": \"split\"");
        String block = page.substring(from, page.indexOf("\"name\": \"unlisted\""));
        assertTrue(block.contains("\"value\": \"true\","));
        assertTrue(block.contains("{\"value\": \"true\", \"label\": \"True\"}"));
        assertTrue(block.contains("{\"value\": \"false\", \"label\": \"False\"}"));
    }
}
