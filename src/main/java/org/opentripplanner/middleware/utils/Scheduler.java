package org.opentripplanner.middleware.utils;

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
}
