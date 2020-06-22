package org.opentripplanner.middleware.bugsnag.jobs;

import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

/**
 * Job to make event data request to Bugsnag and store the initial response.
 */
public class BugsnagEventRequestJob implements Runnable {

    private static TypedPersistence<BugsnagEventRequest> eventDataRequest = Persistence.bugsnagEventRequests;

    /**
     * On each cycle, make an event data request to Bugsnag and store the response for retrieval by Bugsnag event job
     */
    public void run() {
        BugsnagEventRequest request = BugsnagDispatcher.makeEventDataRequest();
        if (request != null) {
            eventDataRequest.create(request);
        }
    }
}
