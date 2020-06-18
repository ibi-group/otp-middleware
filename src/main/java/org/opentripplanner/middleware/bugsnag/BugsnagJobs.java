package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Schedule the Bugsnag jobs
 */
public class BugsnagJobs {

    private static final Logger LOG = LoggerFactory.getLogger(BugsnagReporter.class);

    /**
     * Define the frequency of each Bugsnag job and schedule
     */
    public static void initialize() {
        BugsnagEventRequestJob bugsnagEventRequestJob = new BugsnagEventRequestJob();
        Scheduler.scheduleJob(bugsnagEventRequestJob, 0, 5, TimeUnit.MINUTES);
        LOG.debug("Scheduled Bugsnag event request job");

        BugsnagEventJob bugsnagEventJob = new BugsnagEventJob();
        Scheduler.scheduleJob(bugsnagEventJob, 1, 1, TimeUnit.MINUTES);
        LOG.debug("Scheduled Bugsnag event job");
    }
}
