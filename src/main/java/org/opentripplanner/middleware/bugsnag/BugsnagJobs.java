package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagProjectJob;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsInt;

/**
 * Schedule the Bugsnag jobs. Only ten API requests can be made to Bugsnag within a minute. If additional jobs are added
 * it is important to make sure that no more than ten jobs are scheduled per minute. Any more than this will be rejected
 * by Bugsnag.
 */
public class BugsnagJobs {

    private static final int BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES =
        getConfigPropertyAsInt("BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES", 5);

    private static final int BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES =
        getConfigPropertyAsInt("BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES", 1);

    private static final int BUGSNAG_PROJECT_JOB_DELAY_IN_MINUTES =
        getConfigPropertyAsInt("BUGSNAG_PROJECT_JOB_DELAY_IN_MINUTES", 1);

    private static final Logger LOG = LoggerFactory.getLogger(BugsnagReporter.class);

    /**
     * Schedule each Bugsnag job based on the delay configuration parameters
     */
    public static void initialize() {
        BugsnagEventRequestJob bugsnagEventRequestJob = new BugsnagEventRequestJob();

        Scheduler.scheduleJob(
            bugsnagEventRequestJob,
            0,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.debug("Scheduled Bugsnag event request job");

        BugsnagEventJob bugsnagEventJob = new BugsnagEventJob();

        Scheduler.scheduleJob(
            bugsnagEventJob,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES + BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.debug("Scheduled Bugsnag event job");

        BugsnagProjectJob bugsnagProjectJob = new BugsnagProjectJob();

        Scheduler.scheduleJob(
            bugsnagProjectJob,
            0,
            BUGSNAG_PROJECT_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.debug("Scheduled Bugsnag project job");
    }
}
