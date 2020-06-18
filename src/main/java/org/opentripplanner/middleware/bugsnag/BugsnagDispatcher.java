package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.response.Organization;
import org.opentripplanner.middleware.bugsnag.response.Project;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Class to provide Bugsnag information bu constructing required API calls to Bugsnag
 */
public class BugsnagDispatcher {

    private static final String BUGSNAG_API_URL = "https://api.bugsnag.com";
    private static final String BUGSNAG_API_KEY = getConfigPropertyAsText("BUGSNAG_API_KEY");
    private static final String BUGSNAG_ORGANIZATION = getConfigPropertyAsText("BUGSNAG_ORGANIZATION");

    private static Organization ORGANIZATION = null;
    public static HashMap<String, Project> PROJECTS = new HashMap<>();

    private static HashMap<String, String> BUGSNAG_HEADERS = new HashMap<>();

    // if BUGSNAG_REPORTING_WINDOW_IN_DAYS changes the error.since value in this JSON file will need to be updated
    private static String EVENT_DATA_REQUEST_FILTER;

    private static int CONNECTION_TIMEOUT_IN_SECONDS = 5;


    static {
        BUGSNAG_HEADERS.put("Authorization", "token " + BUGSNAG_API_KEY);
        BUGSNAG_HEADERS.put("Accept", "application/json; version=2");
        BUGSNAG_HEADERS.put("Content-Type", "application/json");

        getOrganization();
        getProjects();

        EVENT_DATA_REQUEST_FILTER
            = FileUtils.getFileContents("src/main/resources/org/opentripplanner/middleware/eventDataRequestFilter.json");
    }

    /**
     * Make the initial event data request to Bugsnag. This triggers an asynchronous job to prepare the data for one
     * single download. The returned event data request can be used to check the status of the request
     */
    public static BugsnagEventRequest makeEventDataRequest() {

        if (ORGANIZATION == null) {
            return null;
        }

        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL,
            "/organizations/" + ORGANIZATION.id + "/event_data_requests",
            null);

        String response = HttpUtils.httpRequest(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.POST,
            BUGSNAG_HEADERS, EVENT_DATA_REQUEST_FILTER);

        BugsnagEventRequest eventDataRequest = JsonUtils.getPOJOFromJSON(response, BugsnagEventRequest.class);

        return eventDataRequest;
    }

    /**
     * Get a previously created event data request. A status of 'complete' signals that the requested data is ready to
     * be downloaded from the populated url parameter.
     */
    public static BugsnagEventRequest getEventDataRequest(BugsnagEventRequest originalEventDataRequest) {

        if (ORGANIZATION == null) {
            return null;
        }

        String endpoint = "/organizations/" + ORGANIZATION.id + "/event_data_requests/" +
            originalEventDataRequest.eventDataRequestId;

        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL, endpoint, null);

        String response = HttpUtils.httpRequest(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET, BUGSNAG_HEADERS, null);

        BugsnagEventRequest eventDataRequest = JsonUtils.getPOJOFromJSON(response, BugsnagEventRequest.class);

        return eventDataRequest;
    }


    /**
     * Get a single organisation matching the organization held in the property file
     */
    private static void getOrganization() {

        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL, "/user/organizations/", null);
        String response = HttpUtils.httpRequest(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET, BUGSNAG_HEADERS, null);
        Organization[] organizations = JsonUtils.getPOJOFromJSON(response, Organization[].class);

        for (Organization org : organizations) {
            if (org.name.equalsIgnoreCase(BUGSNAG_ORGANIZATION)) {
                ORGANIZATION = org;
                break;
            }
        }
    }

    /**
     * Get all projects configured under the provided organization id
     */
    private static void getProjects() {
        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL,
            "/organizations/" + ORGANIZATION.id + "/projects",
            null);

        String response = HttpUtils.httpRequest(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET, BUGSNAG_HEADERS, null);

        Project[] projects = JsonUtils.getPOJOFromJSON(response, Project[].class);
        if (projects != null) {
            for (Project project : projects) {
                PROJECTS.put(project.id, project);
            }
        }
    }

    /**
     * Get requested event data from unique url generated by Bugsnag once the requested data is ready
     */
    public static List<BugsnagEvent> getEventData(BugsnagEventRequest originalEventDataRequest) {
        URI uri = HttpUtils.buildUri(originalEventDataRequest.url, null, null);

        String response = HttpUtils.httpRequest(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            null,
            null);

        BugsnagEvent[] eventData = JsonUtils.getPOJOFromJSON(response, BugsnagEvent[].class);
        return (eventData == null) ? new ArrayList<>() : Arrays.asList(eventData);
    }
}
