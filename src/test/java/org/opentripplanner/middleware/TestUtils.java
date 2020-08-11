package org.opentripplanner.middleware;

import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TestUtils {

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar (String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    public static <T> T getResourceFileContentsAsJSON (String resourcePathName, Class<T> clazz) throws IOException {
        return FileUtils.getFileContentsAsJSON(
            String.format("src/test/resources/org/opentripplanner/middleware/%s", resourcePathName),
            clazz
        );
    }

    // TODO: Use this stuff for mocking time in TripMonitorTest.
    private static Clock clock = Clock.systemDefaultZone();
    private static ZoneId zoneId = ZoneId.systemDefault();

    public static LocalDateTime now() {
        return LocalDateTime.now(getClock());
    }

    public static void useFixedClockAt(LocalDateTime date){
        clock = Clock.fixed(date.atZone(zoneId).toInstant(), zoneId);
    }

    public static void useSystemDefaultZoneClock(){
        clock = Clock.systemDefaultZone();
    }

    private static Clock getClock() {
        return clock;
    }
}
