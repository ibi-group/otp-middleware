package org.opentripplanner.middleware.testutils;

import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;
import java.time.LocalTime;

public class CommonTestUtils {

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    public static <T> T getTestResourceAsJSON(String resourcePathName, Class<T> clazz) throws IOException {
        return FileUtils.getFileContentsAsJSON(OtpMiddlewareTestEnvironment.TEST_RESOURCE_PATH + resourcePathName, clazz);
    }

    public static String getTestResourceAsString(String resourcePathName) throws IOException {
        return FileUtils.getFileContents(OtpMiddlewareTestEnvironment.TEST_RESOURCE_PATH + resourcePathName);
    }

    /**
     * Inserts strings such as "5:40 PM" within other text with the correct spacing character.
     */
    public static String usTime(int hour, int minute, String message) {
        return String.format(message, LocalTime.of(hour, minute).format(DateTimeUtils.US_TIME_FORMATTER));
    }
}
