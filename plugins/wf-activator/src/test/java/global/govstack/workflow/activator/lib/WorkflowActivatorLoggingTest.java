package global.govstack.workflow.activator.lib;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The plugin's log lines, as the platform's LogUtil writes them (commons-logging, carried to Log4j 2 on
 * this classpath). A post-processing tool is handed the saved record's id as the property "recordId" and
 * no rows; that is the normal case and must not be logged at ERROR. What the plugin does with the
 * properties is unchanged, and the one case that is a real failure — no rows and no id — is still an ERROR.
 */
public class WorkflowActivatorLoggingTest {

    private static final String LOGGER = WorkflowActivator.class.getName();

    private final List<LogEvent> events = new ArrayList<>();
    private AbstractAppender appender;
    private Logger logger;
    private Level levelBefore;

    @Before
    public void attach() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        logger = ctx.getLogger(LOGGER);
        levelBefore = logger.getLevel();
        appender = new AbstractAppender("recording", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
    }

    @After
    public void detach() {
        logger.removeAppender(appender);
        logger.setLevel(levelBefore);
        appender.stop();
    }

    private List<String> errors() {
        List<String> out = new ArrayList<>();
        for (LogEvent e : events) {
            if (e.getLevel().isMoreSpecificThan(Level.ERROR)) {
                out.add(e.getMessage().getFormattedMessage());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> prepare(WorkflowActivator p, Map<String, Object> props, boolean passFormData)
            throws Exception {
        Method m = WorkflowActivator.class.getDeclaredMethod("prepareWorkflowVariables", Map.class, boolean.class);
        m.setAccessible(true);
        return (Map<String, String>) m.invoke(p, props, passFormData);
    }

    /** Control: the capture sees the plugin's ERROR lines. No rows and no id is a real failure and stays an ERROR. */
    @Test
    public void noRowsAndNoRecordId_isStillAnError() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("processName", "x");
        Map<String, String> vars = prepare(new WorkflowActivator(), props, true);

        assertTrue("expected the ERROR line, got " + errors(),
                errors().stream().anyMatch(m -> m.contains("No FormRowSet found in properties - cannot extract record ID")));
        assertFalse(vars.containsKey("recordId"));
    }

    /** The statement form's case: the platform passes "recordId" and no rows. Not an error. */
    @Test
    public void noRowsButRecordId_isNotAnError() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("recordId", "5ddf88cf-28f6-4701-a4f8-93f498c2040b");
        props.put("passFormData", "true");
        prepare(new WorkflowActivator(), props, true);

        assertEquals("no ERROR line expected", new ArrayList<String>(), errors());
    }

    /** The variables built are what they were: without rows, only the two system variables. */
    @Test
    public void noRowsButRecordId_variablesUnchanged() throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("recordId", "r-1");
        Map<String, String> vars = prepare(new WorkflowActivator(), props, true);

        assertEquals(2, vars.size());
        assertTrue(vars.containsKey("formSubmissionTime"));
        assertEquals(WorkflowActivator.class.getName(), vars.get("activatorPlugin"));
    }

    /** The list of properties received is a debugging aid, not an error. */
    @Test
    public void propertiesListIsNotLoggedAtError() {
        Map<String, Object> props = new HashMap<>();
        props.put("recordId", "r-1");
        new WorkflowActivator().execute(props);   // no serviceId configured: returns after its own ERROR

        assertFalse("the properties list was logged at ERROR: " + errors(),
                errors().stream().anyMatch(m -> m.contains("DEBUG: WorkflowActivator")));
        assertTrue("control: the missing serviceId is still an ERROR",
                errors().stream().anyMatch(m -> m.contains("ServiceId is required but not configured")));
    }
}
