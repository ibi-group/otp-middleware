package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsInt;

/**
 * Schedule the Bugsnag jobs
 */
public class BugsnagJobs {

    private static final int BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES
        = getConfigPropertyAsInt("BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES", 5);

    private static final int BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES
        = getConfigPropertyAsInt("BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES", 1);
    private static final Logger LOG = LoggerFactory.getLogger(BugsnagReporter.class);

    /**
     * Define the frequency of each Bugsnag job and schedule
     */
    public static void initialize() {
        BugsnagEventRequestJob bugsnagEventRequestJob = new BugsnagEventRequestJob();

        Scheduler.scheduleJob(bugsnagEventRequestJob, 0,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.debug("Scheduled Bugsnag event request job");

        BugsnagEventJob bugsnagEventJob = new BugsnagEventJob();

        Scheduler.scheduleJob(bugsnagEventJob,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES + BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.debug("Scheduled Bugsnag event job");
    }
}
