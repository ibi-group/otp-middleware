package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventHandlingJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.models.BugsnagConfig;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

/**
 * Schedule the Bugsnag jobs. Only ten API requests can be made to Bugsnag within a minute. If additional jobs are added
 * it is important to make sure that no more than ten jobs are scheduled per minute. Any more than this will be rejected
 * by Bugsnag.
 */
public class BugsnagJobs {

    private static final int BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS =
        getConfigPropertyAsInt("BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS", 24);

    private static final int BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES =
        getConfigPropertyAsInt("BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES", 1);

    private static final Logger LOG = LoggerFactory.getLogger(BugsnagJobs.class);

    /**
     * Schedule each Bugsnag job based on the delay configuration parameters
     */
    public static void initialize() {
        // First, send initial event data requests to seed the database with events on start-up. These requests will be
        // processed by the BugsnagEventHandlingJob once it has been scheduled below.
        String configId = "1";
        BugsnagConfig bugsnagConfig = Persistence.bugsnagConfig.getOneFiltered(eq("configId", configId));
        if (bugsnagConfig == null || bugsnagConfig.seedEventData) {
            BugsnagEventRequestJob.triggerNewEventDataRequest(BugsnagDispatcher.EventDataRequestType.SEED);
            if (bugsnagConfig == null) {
                // First time seeding event data. Create config entry to record this so the process is not repeated on
                // next start-up.
                bugsnagConfig = new BugsnagConfig(configId, false);
                Persistence.bugsnagConfig.create(bugsnagConfig);
                LOG.info("Initiated seeding of event data requests");
            } else {
                // Event data seeding has already happened (hence record), but the value has been reset manually. This
                // is useful if we want to reseed. E.g. Set seedEventData = false via Mongo DB and restart otp middleware.
                bugsnagConfig.seedEventData = false;
                Persistence.bugsnagConfig.replace(bugsnagConfig.id, bugsnagConfig);
                LOG.info("Initiated reseeding of event data requests");
            }
        }

        LOG.info("Scheduling Bugsnag event data requests for every {} hours", BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS);
        // Next, handle sending event data requests to Bugsnag on a daily basis.
        Scheduler.scheduleJob(
            new BugsnagEventRequestJob(),
            0,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS,
            TimeUnit.HOURS);

        LOG.info("Scheduling Bugsnag request handling for every {} minute(s)", BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES);
        // Next, schedule the job to handle the event data that comes back and sync with the existing database.
        Scheduler.scheduleJob(
            new BugsnagEventHandlingJob(),
            0,
            BUGSNAG_EVENT_JOB_DELAY_IN_MINUTES,
            TimeUnit.MINUTES);


    }
}
