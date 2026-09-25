package global.govstack.workflow.activator.lib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The version the plugin reports to the platform (shown in Manage Plugins and written in its registry).
 * The build of 24 September 2026, which logs the no-rows case at INFO, still reported 8.0.6, the version
 * of the build it replaced, so the two could be told apart only by their checksums. The version is
 * raised so that a build carrying that correction is told from the former one by its version.
 */
public class WorkflowActivatorVersionTest {

    private static final String FORMER_VERSION = "8.0.6";

    @Test
    public void reportsAVersionAboveTheFormerBuilds() {
        String version = new WorkflowActivator().getVersion();
        assertNotEquals("the version of the former build", FORMER_VERSION, version);
        assertTrue("above " + FORMER_VERSION + ": " + version, compare(version, FORMER_VERSION) > 0);
    }

    @Test
    public void reportsVersion807() {
        assertEquals("8.0.7", new WorkflowActivator().getVersion());
    }

    /** Numeric comparison of dotted versions, part by part. */
    private static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }
}
