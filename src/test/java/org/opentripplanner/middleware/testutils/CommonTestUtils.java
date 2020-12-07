package org.opentripplanner.middleware.testutils;

import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;

public class CommonTestUtils {
    /**
     * Whether the end-to-end environment variable is enabled.
     */
    public static final boolean IS_END_TO_END = getBooleanEnvVar("RUN_E2E");

    private static final String TEST_RESOURCE_PATH = "src/test/resources/org/opentripplanner/middleware/";

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    public static <T> T getTestResourceAsJSON(String resourcePathName, Class<T> clazz) throws IOException {
        return FileUtils.getFileContentsAsJSON(TEST_RESOURCE_PATH + resourcePathName, clazz);
    }

    public static String getTestResourceAsString(String resourcePathName) throws IOException {
        return FileUtils.getFileContents(TEST_RESOURCE_PATH + resourcePathName);
    }

}
