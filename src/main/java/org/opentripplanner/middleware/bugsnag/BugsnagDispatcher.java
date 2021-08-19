package org.opentripplanner.middleware.bugsnag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Responsible for getting {@link BugsnagEvent} information from Bugsnag. This is done by making calls to Bugsnag's API
 * endpoints with a valid authorization token ({@link #BUGSNAG_API_KEY}).
 *
 * A bugsnag API key is a key that is unique to an individual Bugsnag user. This key can be obtained by logging into
 * Bugsnag (https://app.bugsnag.com), clicking on settings (top right hand corner) -> “My account settings”. From here
 * select “Personal auth tokens” and then “Generate new token”.
 *
 * The following Bugsnag API endpoints are currently used:
 *
 * https://api.bugsnag.com/projects/<project_id>/event_data_requests
 * https://api.bugsnag.com/projects/<project_id>/event_data_requests/<event_data_request_id>
 */
public class BugsnagDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(BugsnagDispatcher.class);

    private static final String BUGSNAG_API_URL = "https://api.bugsnag.com";
    public static final int TEST_BUGSNAG_PORT = 8089;
    public static final String TEST_BUGSNAG_DOMAIN = String.format("http://localhost:%d", TEST_BUGSNAG_PORT);
    private static String baseBugsnagUrl = BUGSNAG_API_URL;
    private static final String BUGSNAG_API_KEY = getConfigPropertyAsText("BUGSNAG_API_KEY");
    public static final int BUGSNAG_REPORTING_WINDOW_IN_DAYS =
        getConfigPropertyAsInt("BUGSNAG_REPORTING_WINDOW_IN_DAYS", 14);

    /**
     * Used to override the base url for making requests to Bugsnag. This is primarily used for testing purposes to set
     * the url to something that is stubbed with WireMock.
     */
    public static void setBaseUsersUrl(String url) {
        baseBugsnagUrl = url;
    }

    /**
     * Headers that are required by Bugsnag for each request.
     */
    private static final Map<String, String> BUGSNAG_HEADERS = Map.of(
        "Authorization", "token " + BUGSNAG_API_KEY,
        "Accept", "application/json; version=2",
        "Content-Type", "application/json"
    );

    private static final int CONNECTION_TIMEOUT_IN_SECONDS = 5;

    /**
     * Build the event request filter that will be passed to Bugsnag with every event data request. The request filter
     * constructed is as follows:
     *
     * {
     *   "filters": {
     *     "event.since": [
     *       {
     *         "type": "eq",
     *         "value": "14d"
     *       }
     *     ]
     *   }
     * }
     *
     */
    private static String buildEventRequestFilter(int reportingWindow) {
        final ObjectMapper mapper = new ObjectMapper();

        ObjectNode reportingWindowCondition = mapper.createObjectNode();

        // Specifies how far in the past events should be retrieved. Instead of specifying a date, Bugsnag allows a
        // number of days to be defined. It is very important to include the 'd' value, else the filter fails.
        // FIXME: It might be better to just build the event data request filter with "hours since" rather than days
        //  to be more precise... but maybe it doesn't really matter.
        reportingWindowCondition.put("type", "eq");
        reportingWindowCondition.put("value", reportingWindow + "d");

        // Defines the node which contains the event since filter
        ArrayNode sinceFilters = mapper.createArrayNode();
        sinceFilters.add(reportingWindowCondition);

        // Defines the event since wrapper
        ObjectNode filters = mapper.createObjectNode();
        filters.set("event.since", sinceFilters);

        // Defines the filters wrapper
        ObjectNode json = mapper.createObjectNode();
        json.set("filters", filters);
        return json.toString();
    }

    /**
     * Shorthand to create a Bugsnag event data request. This triggers an asynchronous job to prepare the data for one
     * single download. The returned event data request can be used to check the status of the request.
     *
     * The “create an event data request” allows event data for a given project to be collated in an asynchronous job
     * by Bugsnag. Once this job has completed a bespoke URL is provided where this data can be downloaded. Information on
     * this approach along with the filter parameters can be reviewed here:
     *
     * https://bugsnagapiv2.docs.apiary.io/#reference/projects/event-data-requests/create-an-event-data-request
     */
    public static BugsnagEventRequest newEventDataRequest(String projectId, int daysInPast) {
        return makeEventDataRequest(null, projectId, daysInPast);
    }

    /**
     * Shorthand to check a previously created Bugsnag event data request.
     */
    public static BugsnagEventRequest checkEventDataRequest(String eventDataRequestId, String projectId) {
        return makeEventDataRequest(eventDataRequestId, projectId, -1);
    }

    /**
     * Get a previously created event data request (if id is non-null) OR create a new request. A status of 'complete'
     * signals that the requested data is ready to be downloaded from the populated url parameter.
     *
     * More here: https://bugsnagapiv2.docs.apiary.io/#reference/projects/event-data-requests/check-the-status-of-an-event-data-request
     */
    public static BugsnagEventRequest makeEventDataRequest(
        String eventDataRequestId,
        String projectId,
        int daysInPast
    ) {
        // Create new request if null ID is provided.
        boolean create = eventDataRequestId == null;
        URI eventDataRequestUri = HttpUtils.buildUri(
            baseBugsnagUrl,
            "projects", projectId, "event_data_requests", eventDataRequestId
        );
        LOG.debug("Making Bugsnag request: {}", eventDataRequestUri);
        String filter = create
            ? buildEventRequestFilter(daysInPast)
            : null;
        HttpResponseValues response = HttpUtils.httpRequestRawResponse(
            eventDataRequestUri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            create ? HttpMethod.POST : HttpMethod.GET,
            BUGSNAG_HEADERS,
            filter,
            true
        );
        try {
            return BugsnagEventRequest.createFromRequest(response, projectId, daysInPast);
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag(
                "Failed to parse Bugsnag event data response",
                eventDataRequestUri,
                e
            );
            return null;
        }
    }

    /**
     * Get requested event data from unique url generated by Bugsnag once the requested data is ready.
     */
    public static List<BugsnagEvent> getEventData(String eventDataRequestUrl) {
        URI eventDataRequestUri = HttpUtils.buildUri(eventDataRequestUrl);
        LOG.debug("Making GET Bugsnag request: {}", eventDataRequestUri);
        HttpResponseValues events = HttpUtils.httpRequestRawResponse(
            eventDataRequestUri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpMethod.GET,
            null,
            null,
            true
        );
        return JsonUtils.getPOJOFromHttpBodyAsList(events, BugsnagEvent.class);
    }
}
