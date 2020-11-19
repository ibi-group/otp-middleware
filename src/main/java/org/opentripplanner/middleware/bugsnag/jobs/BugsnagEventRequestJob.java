package org.opentripplanner.middleware.bugsnag.jobs;

import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This job is responsible for triggering event data requests to Bugsnag and storing the response. These requests happen
 * asynchronously and must be monitored over time, which is the task of {@link BugsnagEventHandlingJob}.
 *
 * This architecture is informed by Bugsnag's limit of ten API requests per minute (with the current pricing plan). This
 * places too greater limit on confidently being able to extract error and event information via the error and event
 * endpoints. Bugsnag provides a mechanism to extract event information from across all projects and events.
 *
 * The “create an event data request” allows event data for a given organization to be collated in an asynchronous job
 * by Bugsnag. Once this job has completed a bespoke URL is provided where this data can be downloaded. Information on
 * this approach along with the filter parameters can be reviewed here:
 *
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/event-data-requests/create-an-event-data-request
 */
public class BugsnagEventRequestJob implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(BugsnagEventRequestJob.class);

    /**
     * On each cycle, make an event data request to Bugsnag and store the response for retrieval by
     * {@link BugsnagEventHandlingJob}.
     */
    public void run() {
        BugsnagEventRequest request = BugsnagDispatcher.newEventDataRequest();
        if (request != null) {
            LOG.debug("Triggered Bugsnag event request (current status={})", request.status);
            Persistence.bugsnagEventRequests.create(request);
        } else {
            LOG.error("Unknown error encountered while triggering Bugsnag event data request!");
        }
    }
}
