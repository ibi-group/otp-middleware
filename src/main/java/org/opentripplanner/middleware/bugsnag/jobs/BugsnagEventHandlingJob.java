package org.opentripplanner.middleware.bugsnag.jobs;

import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.bugsnag.BugsnagJobs;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.models.MonitoredComponent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This job is responsible for maintaining Bugsnag event data. This is achieved by managing the event request jobs
 * triggered by {@link BugsnagEventRequestJob}, obtaining event data from Bugsnag storage and removing stale events.
 *
 * Event requests triggered by {@link BugsnagEventRequestJob} are not completed immediately. Rather a job is started by
 * Bugsnag and the 'pending' event request is returned. This event request is then checked with Bugsnag every minute
 * until the status becomes 'completed'. At this point the event data compiled by the original request made by
 * {@link BugsnagEventHandlingJob} is available for download from a unique URL now present in the updated event request.
 * This is downloaded and saved to Mongo. Any event data that is older than the reporting window is then deleted.
 */
public class BugsnagEventHandlingJob implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(BugsnagEventHandlingJob.class);
    private static final int BUGSNAG_REPORTING_WINDOW_IN_DAYS = ConfigUtils.getConfigPropertyAsInt(
        "BUGSNAG_REPORTING_WINDOW_IN_DAYS",
        14
    );

    /**
     * On each cycle get the latest event data request from Mongo. These event requests are initially populated by
     * {@link BugsnagEventRequestJob}. If the latest request has been fulfilled by Bugsnag, add all new events to Mongo
     * and remove any that have expired according to the Bugsnag reporting window.
     */
    public void run() {
        // Get latest "incomplete" request per project.
        Set<String> projectIds = MonitoredComponent.getComponentsByProjectId().keySet();
        for (String projectId : projectIds) {
            BugsnagEventRequest latestIncompleteRequest = BugsnagJobs.getLatestIncompleteRequestForProject(projectId);
            if (latestIncompleteRequest == null) {
                LOG.debug("No pending event data requests found for project {}.", projectId);
                continue;
            }
            // Handle the request data if it exists.
            refreshEventRequest(latestIncompleteRequest);
        }
        // FIXME: Do we want to remove these stale events? This could be confusing for users of the system and could cause
        //  issues tracing down issues over time. Maybe the removal window could be greater than the reporting window
        //  (BUGSNAG_REPORTING_WINDOW_IN_DAYS) to give more flexibility?
        removeStaleEvents();
    }

    /**
     * Refresh the event request to check status and update event data accordingly.
     */
    private void refreshEventRequest(BugsnagEventRequest request) {
        // Refresh the event data request.
        BugsnagEventRequest refreshedRequest = request.refreshEventDataRequest();
        if (refreshedRequest == null) {
            LOG.error("Failed to refresh event request");
            return;
        }
        switch (refreshedRequest.status.toLowerCase()) {
            case "completed":
                // First, delete the last completed request for the project.
                BugsnagEventRequest latestCompletedRequestForProject =
                    BugsnagJobs.getLatestCompletedRequestForProject(request.projectId);
                latestCompletedRequestForProject.delete();
                // Next, replace the newly completed request.
                request.update(refreshedRequest);
                // Finally, get and store the new events from the completed request and notify users.
                List<BugsnagEvent> newEvents = getNewEvents(refreshedRequest);
                if (newEvents.size() > 0) {
                    LOG.info("Found {} new events. Storing and notifying subscribed admin users.", newEvents.size());
                    Persistence.bugsnagEvents.createMany(newEvents);
                    // Notify any subscribed users about new events.
                    BugsnagReporter.sendEmailForEvents(newEvents.size());
                }
                break;
            case "expired":
                // First, remove the expired request.
                LOG.info("Event data request for project {} has expired. Removing from database.", request.projectId);
                request.delete();
                // Next, immediately trigger a new event data request to replace the expired one.
                LOG.info("Triggering a new event data request for project {} to replace previously expired.", request.projectId);
                BugsnagEventRequestJob.triggerEventDataRequestForProject(request.projectId, request.daysInPast);
                break;
            default: {
                // Request not completed by Bugsnag yet. Update the event request to record new status (this may not have
                // changed) and await the next cycle/refresh.
                request.update(refreshedRequest);
            }
        }
    }

    /**
     * Get the event data from the completed {@link BugsnagEventRequest}.
     */
    private List<BugsnagEvent> getNewEvents(BugsnagEventRequest request) {
        // Get list of all currently tracked event ids.
        HashSet<String> currentEventIds = Persistence.bugsnagEvents.getAll()
            .map(event -> event.eventDataId)
            .into(new HashSet<>());
        // Get and filter bugsnag events.
        return request.getEventData().stream()
            // Include error events that do not already exist in our database.
            .filter(event -> !currentEventIds.contains(event.eventDataId))
            .collect(Collectors.toList());
    }

    /**
     * Remove events that are older than the reporting window.
     */
    private void removeStaleEvents() {
        Date reportingWindowCutoff = Date.from(
            DateTimeUtils
                .nowAsLocalDate()
                .minusDays(BUGSNAG_REPORTING_WINDOW_IN_DAYS + 1)
                .atTime(LocalTime.MIDNIGHT)
                .atZone(DateTimeUtils.getSystemZoneId())
                .toInstant()
        );
        Persistence.bugsnagEvents.removeFiltered(Filters.lte("receivedAt", reportingWindowCutoff));
    }

}
