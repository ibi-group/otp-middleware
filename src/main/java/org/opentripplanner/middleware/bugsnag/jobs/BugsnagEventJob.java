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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsInt;

/**
 * This job is responsible for maintaining Bugsnag event data. This is achieved by managing the event request jobs
 * triggered by {@link BugsnagEventJob}, obtaining event data from Bugsnag storage and removing stale events.
 *
 * Event requests triggered by {@link BugsnagEventJob} are not completed immediately, instead a job is initiated by
 * Bugsnag and a 'pending' event request is returned. This event request is then checked with Bugsnag every minute
 * until the status becomes 'completed'. At this point the event data compiled by the original request made by
 * {@link BugsnagEventJob} is available for download from a unique URL now present in the updated event request. This is
 * downloaded and saved to Mongo. Any event data that is older than the reporting window is then deleted.
 */
public class BugsnagEventJob implements Runnable {

    private static final int BUGSNAG_REPORTING_WINDOW_IN_DAYS
        = getConfigPropertyAsInt("BUGSNAG_REPORTING_WINDOW_IN_DAYS", 14);

    private static TypedPersistence<BugsnagEventRequest> bugsnagEventRequests = Persistence.bugsnagEventRequests;
    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;

    /**
     * On each cycle get the newest event data request from Mongo. If that request has been fulfilled by Bugsnag, add
     * all new events to Mongo and remove all that are older than the Bugsnag reporting window.
     */
    public void run() {
        Bson filter = Filters.ne("status", "complete");
        BugsnagEventRequest originalRequest =
            bugsnagEventRequests.getOneFiltered(filter, Sorts.descending("dateCreated"));
        if (originalRequest != null) {
            manageEvents(originalRequest);
        }
        removeStaleEvents();
    }

    /**
     * Confirm that the event request has completed and update event data accordingly.
     */
    private void manageEvents(BugsnagEventRequest originalRequest) {

        BugsnagEventRequest currentRequest = BugsnagDispatcher.getEventDataRequest(originalRequest);
        if (!currentRequest.status.equalsIgnoreCase("completed")) {
            // request not completed by Bugsnag yet
            return;
        }

        removeStaleEventRequests(originalRequest);

        // get event data produced from original event request from Bugsnag storage
        List<BugsnagEvent> events = BugsnagDispatcher.getEventData(currentRequest);

        //TODO Potential bottleneck depending on the number of projects and events. Dropping all rows and than
        // inserting would speed this up, but you run the risk of return nothing to the admin dashboard.

        // add new events
        for (BugsnagEvent bugsnagEvent : events) {
            Set<Bson> clauses = new HashSet<>();
            clauses.add(eq("errorId", bugsnagEvent.errorId));
            clauses.add(eq("projectId", bugsnagEvent.projectId));
            clauses.add(eq("receivedAt", bugsnagEvent.receivedAt));
            if (bugsnagEvents.getOneFiltered(Filters.and(clauses)) == null) {
                // only create event if not present
                bugsnagEvents.create(bugsnagEvent);
            }
        }
    }

    /**
     * Remove event requests which have been completed or superseded.
     */
    private void removeStaleEventRequests(BugsnagEventRequest latestRequest) {
        Bson filter = Filters.lte("dateCreated", latestRequest.dateCreated);
        bugsnagEventRequests.removeFiltered(filter);
    }

    /**
     * Remove events that are older than the reporting window.
     */
    private void removeStaleEvents() {
        LocalDate startOfReportingWindow = LocalDate
            .now()
            .minusDays(BUGSNAG_REPORTING_WINDOW_IN_DAYS + 1);
        startOfReportingWindow.atTime(LocalTime.MIDNIGHT);

        Date date = Date.from(startOfReportingWindow.atTime(LocalTime.MIDNIGHT)
            .atZone(ZoneId.systemDefault())
            .toInstant());

        Bson filter = Filters.lte("receivedAt", date);
        bugsnagEvents.removeFiltered(filter);
    }
}


