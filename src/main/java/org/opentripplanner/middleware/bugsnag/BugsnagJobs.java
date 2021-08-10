package org.opentripplanner.middleware.bugsnag;

import com.google.common.collect.Sets;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventHandlingJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.models.MonitoredComponent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.bugsnag.BugsnagDispatcher.BUGSNAG_REPORTING_WINDOW_IN_DAYS;
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
        // Get latest sync tracker from database to determine how far back we need to sync.
        Set<String> projectsRequested = seedDataIfNeeded();
        Set<String> allProjects = MonitoredComponent.getComponentsByProjectId().keySet();
        Set<String> projectsNotImmediatelyRequested = Sets.difference(allProjects, projectsRequested);
        LOG.info("Scheduling Bugsnag event data requests for every {} hours", BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS);
        // Schedule projects not included in the seed data requests to begin immediately.
        Scheduler.scheduleJob(
            new BugsnagEventRequestJob(projectsNotImmediatelyRequested),
            0,
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS,
            TimeUnit.HOURS);
        // Schedule projects already requested to offset by the job delay period.
        Scheduler.scheduleJob(
            new BugsnagEventRequestJob(projectsNotImmediatelyRequested),
            BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS,
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

    private static Set<String> seedDataIfNeeded() {
        Set<String> projectsRequested = new HashSet<>();
        Set<String> projectIds = MonitoredComponent.getComponentsByProjectId().keySet();
        projectIds.forEach(projectId ->
            {
                int hoursSinceLastRequest = getHoursSinceLastRequest(projectId);
                // Only seed data if days since is greater than one day (otherwise we're duplicating the scheduled
                if (hoursSinceLastRequest > BUGSNAG_EVENT_REQUEST_JOB_DELAY_IN_HOURS) {
                    LOG.debug("Triggered Bugsnag event request for project {}", projectId);
                    BugsnagEventRequestJob.triggerEventDataRequestForProject(projectId, hoursSinceLastRequest / 24);
                    projectsRequested.add(projectId);
                }
            }
        );
        return projectsRequested;
    }

    private static int getHoursSinceLastRequest(String projectId) {
        int hoursSinceLastRequest = 0;
        // Get latest successful request for project.
        BugsnagEventRequest compareRequest = getLatestComparisonRequest(projectId);
        if (compareRequest != null) {
            // Otherwise, determine how many hours have passed since the end of the last request's time window.
            long reportingGapMillis = new Date().getTime() - compareRequest.getTimeWindowEndInMillis();
            // TimeUnit#convert truncates/rounds down, so add one to account for partial hours.
            long hours = TimeUnit.HOURS.convert(reportingGapMillis, TimeUnit.MILLISECONDS);
            hoursSinceLastRequest = Math.toIntExact(hours) + 1;
        }
        return Math.max(hoursSinceLastRequest, BUGSNAG_REPORTING_WINDOW_IN_DAYS);
    }

    private static BugsnagEventRequest getLatestComparisonRequest(String projectId) {
        BugsnagEventRequest lastCompleted = getLatestCompleteRequestForProject(projectId);
        BugsnagEventRequest lastIncomplete = getLatestIncompleteRequestForProject(projectId);
        if (lastIncomplete == null) return lastCompleted;
        boolean lastIsExpired = lastIncomplete.status.equalsIgnoreCase("expired");
        if (lastCompleted == null) return lastIsExpired ? null : lastIncomplete;
        // Determine which request we should use to determine what gap we need to fill.
        // If the latest incomplete is newer and not expired, BugsnagEventHandlingJob will await the completion of that
        // request.
        // TODO: But check to see if we need to fill any gaps that might exist between the incomplete request's time
        //  window and now.
        return lastIncomplete.getTimeWindowEndInMillis() > lastCompleted.getTimeWindowEndInMillis() && !lastIsExpired
            ? lastIncomplete
            : lastCompleted;
    }

    private static BugsnagEventRequest getLatestRequestForProject(String projectId, Bson filter) {
        return Persistence.bugsnagEventRequests.getOneFiltered(
            Filters.and(
                Filters.eq("projectId", projectId),
                filter
            ),
            Sorts.descending("dateCreated")
        );
    }

    public static BugsnagEventRequest getLatestCompleteRequestForProject(String projectId) {
        return getLatestRequestForProject(projectId, Filters.ne("status", "completed"));
    }

    public static BugsnagEventRequest getLatestIncompleteRequestForProject(String projectId) {
        return getLatestRequestForProject(projectId, Filters.eq("status", "completed"));
    }
}
