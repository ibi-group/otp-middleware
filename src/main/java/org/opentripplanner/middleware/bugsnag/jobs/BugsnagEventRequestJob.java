package org.opentripplanner.middleware.bugsnag.jobs;

import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

/**
 * This job is responsible for making event data requests to Bugsnag and storing the response.
 *
 * Bugsnag only allows ten API requests per minute (with the current pricing plan). This places too greater limit on
 * confidently being able to extract error and event information via the organization, project, error and event
 * endpoints. Bugsnag provides a mechanism to extract event information from across all projects and events.
 *
 * The “create an event data request” allows event data for a given organization to be collated in an asynchronous job
 * by Bugsnag. Once this job has completed a bespoke URL is provided where this data can be downloaded. Information on
 * this approach along with the filter parameters can be reviewed here:
 *
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/event-data-requests/create-an-event-data-request
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
