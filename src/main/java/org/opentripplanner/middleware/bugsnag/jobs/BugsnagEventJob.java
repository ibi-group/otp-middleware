package org.opentripplanner.middleware.bugsnag.jobs;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Job to check for new event data requests and confirm that Bugsbag has completed the request. At which point obtain
 * the event data from Bugsnag storage, save to Mongo and remove stale events.
 */
public class BugsnagEventJob implements Runnable {

    private static final String BUGSNAG_REPORTING_WINDOW_IN_DAYS
        = getConfigPropertyAsText("BUGSNAG_REPORTING_WINDOW_IN_DAYS");

    private static TypedPersistence<BugsnagEventRequest> bugsnagEventRequests = Persistence.bugsnagEventRequests;
    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;

    /**
     * On each cycle get the newest event request. Update the events based on the response from Bugsnag and remove
     * stale events.
     */
    public void run() {
        Bson filter = Filters.ne("status", "complete");
        BugsnagEventRequest originalRequest = bugsnagEventRequests.getOneFiltered(filter, Sorts.descending("dateCreated"));
        if (originalRequest != null) {
            manageEvents(originalRequest);
        }
        removeStaleEvents();
    }

    /**
     * Confirm that the event request has completed and update event data accordingly
     */
    private void manageEvents(BugsnagEventRequest originalRequest) {

        BugsnagEventRequest currentRequest = BugsnagDispatcher.getEventDataRequest(originalRequest);
        if (!currentRequest.status.equalsIgnoreCase("completed")) {
            // request not completed by Bugsnag yet
            return;
        }

        // remove event request now that it has been completed
        bugsnagEventRequests.removeById(originalRequest.id);

        // get event data produced from original event request from Bugsnag storage
        List<BugsnagEvent> events = BugsnagDispatcher.getEventData(currentRequest);

        // add new events
        for (BugsnagEvent bugsnagEvent : events) {
            Bson filter = Filters.eq("errorId", bugsnagEvent.errorId);
            if (bugsnagEvents.getOneFiltered(filter) == null) {
                // only create event if not present
                bugsnagEvents.create(bugsnagEvent);
            }
        }
    }

    /**
     * Remove events that are older than the reporting window
     */
    private void removeStaleEvents() {
        LocalDate startOfReportingWindow = LocalDate
            .now()
            .minusDays(Integer.parseInt(BUGSNAG_REPORTING_WINDOW_IN_DAYS) + 1);
        startOfReportingWindow.atTime(LocalTime.MIDNIGHT);

        Date date = Date.from(startOfReportingWindow.atTime(LocalTime.MIDNIGHT)
            .atZone(ZoneId.systemDefault())
            .toInstant());

        Bson filter = Filters.lte("dateCreated", date);
        List<BugsnagEvent> events = bugsnagEvents.getFiltered(filter);
        for (BugsnagEvent event : events) {
            bugsnagEvents.removeById(event.id);
        }
    }
}


