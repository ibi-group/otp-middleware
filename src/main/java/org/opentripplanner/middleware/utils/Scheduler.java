package org.opentripplanner.middleware.utils;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to schedule jobs
 */
public class Scheduler {

    private final static ScheduledExecutorService schedulerService =
        Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Schedule jobs based on the provided job and parameters
     */
    public static void scheduleJob(Runnable job, long initialDelay, long delay, TimeUnit timeUnit) {
        schedulerService.scheduleAtFixedRate(job, initialDelay, delay, timeUnit);
    }

    /**
     * Calculate an initial delay in milliseconds precision, until a start time specified as text
     * string. String must be a valid {@link java.time.format.DateTimeFormatter#ISO_LOCAL_TIME}
     * specification, {@code "03:00"} for example.
     * @param startTime text specification of a local time, such as {@code "03:00"}
     * @return number of milliseconds between now and specified start time, never less than 0
     */
    public static long getInitialDelayMillis(String startTime) throws DateTimeParseException {
        var timeOfDay = LocalTime.parse(startTime);
        var now = DateTimeUtils.nowAsZonedDateTime();
        var startAt = DateTimeUtils.getNextTimeFrom(timeOfDay, now);
        var duration = Duration.between(now, startAt);
        return duration.isNegative() ? 0L : duration.toMillis();
    }
}
