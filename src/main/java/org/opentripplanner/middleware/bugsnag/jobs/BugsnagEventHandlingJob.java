package org.opentripplanner.middleware.bugsnag.jobs;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.models.MonitoredComponent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.opentripplanner.middleware.utils.TemplateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This job is responsible for maintaining Bugsnag event data. This is achieved by managing the event request jobs
 * triggered by {@link BugsnagEventRequestJob}, obtaining event data from Bugsnag storage and removing stale events.
 *
 * Event requests triggered by {@link BugsnagEventRequestJob} are not completed immediately. Rather a job is started by
 * Bugsnag and the 'pending' event request is returned. This event request is then checked with Bugsnag every minute
 * until the status becomes 'completed'. At this point the event data compiled by the original request made by
 * {@link BugsnagEventHandlingJob} is available for download from a unique URL now present in the updated event request. This is
 * downloaded and saved to Mongo. Any event data that is older than the reporting window is then deleted.
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
        // Get latest "incomplete" request.
        BugsnagEventRequest latestRequest = Persistence.bugsnagEventRequests.getOneFiltered(
            Filters.ne("status", "complete"),
            Sorts.descending("dateCreated")
        );
        if (latestRequest != null) {
            // Handle the request data if it exists.
            refreshEventRequest(latestRequest);
        } else {
            LOG.debug("No pending event data requests found.");
        }
        // FIXME: Do we want to remove these stale events? This could be confusing for users of the system and could cause
        //  issues tracing down issues over time.
        removeStaleEvents();
    }

    /**
     * Refresh the event request to check status and update event data accordingly.
     */
    private void refreshEventRequest(BugsnagEventRequest request) {
        // Refresh the event data request.
        BugsnagEventRequest refreshedRequest = request.refreshEventDataRequest();
        if (!refreshedRequest.status.equalsIgnoreCase("completed")) {
            // Request not completed by Bugsnag yet. Return and await the next cycle/refresh.
            // TODO: Update the request data in the database? What if the status has changed since the last fetch?
            return;
        }
        // First, remove "stale" requests (i.e., those that are older than this latest one).
        Persistence.bugsnagEventRequests.removeFiltered(Filters.lte("dateCreated", request.dateCreated));
        // Next, get and store the new events from the completed request and notify users.
        List<BugsnagEvent> newEvents = getNewEvents(refreshedRequest);
        if (newEvents.size() > 0) {
            LOG.info("Found {} new events. Storing and notifying subscribed admin users.", newEvents.size());
            Persistence.bugsnagEvents.createMany(newEvents);
            // Notify any subscribed users about new events.
            sendEmailForEvents(newEvents);
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
        Set<String> trackedProjectIds = MonitoredComponent.getComponentsByProjectId().keySet();
        // Get and filter bugsnag events.
        return request.getEventData().stream()
            // Include error events that map to monitored components and do not already exist in our database.
            .filter(event -> trackedProjectIds.contains(event.projectId) && !currentEventIds.contains(event.eventDataId))
            .collect(Collectors.toList());
    }

    /**
     * Convenience method to send email notification to all subscribed users.
     */
    private void sendEmailForEvents(List<BugsnagEvent> newEvents) {
        // Construct email content.
        String subject = String.format("%d new error events", newEvents.size());
        Map<String, Object> data = Map.of(
            "subject", subject
        );

        String text;
        String html;
        try {
            text = TemplateUtils.renderTemplateWithData("EventErrorsText.ftl", data);
            html = TemplateUtils.renderTemplateWithData("EventErrorsHtml.ftl", data);
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag("Failed to render error event email", e);
            return;
        }

        // Notify subscribed users.
        for (AdminUser adminUser : Persistence.adminUsers.getAll()) {
            if (adminUser.subscriptions.contains(AdminUser.Subscription.NEW_ERROR)) {
                NotificationUtils.sendEmail(adminUser, subject, text, html);
            }
        }
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
