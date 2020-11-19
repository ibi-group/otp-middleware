package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventHandlingJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

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
        if (BugsnagDispatcher.BUGSNAG_ORGANIZATION == null) {
            LOG.error("WARNING: Bugsnag organization is not available. Cannot schedule bugsnag jobs.");
            return;
        }
        LOG.info("Scheduling Bugsnag event data requests for every {} minute(s)", BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES);
        // First, handle sending event data requests to Bugsnag on a regular basis.
        Scheduler.scheduleJob(
            new BugsnagEventRequestJob(),
            0,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);

        LOG.info("Scheduling Bugsnag request handling for every {} minute(s)", BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES);
        // Next, schedule the job to handle the event data that comes back and sync with the existing database.
        Scheduler.scheduleJob(
            new BugsnagEventHandlingJob(),
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_MINUTES + BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);
    }
}
