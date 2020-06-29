package org.opentripplanner.middleware.bugsnag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opentripplanner.middleware.bugsnag.response.Organization;
import org.opentripplanner.middleware.bugsnag.response.Project;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Class to provide Bugsnag information by constructing required API calls to Bugsnag
 */
public class BugsnagDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(BugsnagDispatcher.class);

    private static final String BUGSNAG_API_URL = "https://api.bugsnag.com";
    private static final String BUGSNAG_API_KEY = getConfigPropertyAsText("BUGSNAG_API_KEY");
    private static final String BUGSNAG_ORGANIZATION = getConfigPropertyAsText("BUGSNAG_ORGANIZATION");
    private static final int BUGSNAG_REPORTING_WINDOW_IN_DAYS
        = getConfigPropertyAsInt("BUGSNAG_REPORTING_WINDOW_IN_DAYS", 14);

    private static Organization ORGANIZATION = null;
    private static HashMap<String, Project> PROJECTS = new HashMap<>();

    private static HashMap<String, String> BUGSNAG_HEADERS = new HashMap<>();

    private static ObjectNode eventRequestFilter;

    private static int CONNECTION_TIMEOUT_IN_SECONDS = 5;

    static {
        setBugsnagRequestHeaders();
        buildEventRequestFilter();
        setOrganization();
        setProjects();
    }

    /**
     * Define the mandatory Bugsnag request headers.
     */
    private static void setBugsnagRequestHeaders() {
        BUGSNAG_HEADERS.put("Authorization", "token " + BUGSNAG_API_KEY);
        BUGSNAG_HEADERS.put("Accept", "application/json; version=2");
        BUGSNAG_HEADERS.put("Content-Type", "application/json");
    }

    /**
     * Build the event request filter that will be passed to Bugsnag with every event data request.
     */
    private static void buildEventRequestFilter() {
        final ObjectMapper mapper = new ObjectMapper();
        ObjectNode reportingWindowCondition = mapper.createObjectNode();
        reportingWindowCondition.put("type", "eq");
        reportingWindowCondition.put("value", BUGSNAG_REPORTING_WINDOW_IN_DAYS + "d");

        ArrayNode sinceFilters = mapper.createArrayNode();
        sinceFilters.add(reportingWindowCondition);

        ObjectNode filters = mapper.createObjectNode();
        filters.set("event.since", sinceFilters);

        eventRequestFilter = mapper.createObjectNode();
        eventRequestFilter.set("filters", filters);
    }

    /**
     * Make the initial event data request to Bugsnag. This triggers an asynchronous job to prepare the data for one
     * single download. The returned event data request can be used to check the status of the request.
     */
    public static BugsnagEventRequest makeEventDataRequest() {

        if (ORGANIZATION == null) {
            LOG.error("Required organization is not available. Unable to make event dadta request");
            return null;
        }

        URI uri = HttpUtils.buildUri(
            BUGSNAG_API_URL,
            "/organizations/" + ORGANIZATION.id + "/event_data_requests",
            null);

        String response = HttpUtils.httpRequest(
            uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.POST,
            BUGSNAG_HEADERS,
            eventRequestFilter.toString());

        return JsonUtils.getPOJOFromJSON(response, BugsnagEventRequest.class);
    }

    /**
     * Get a previously created event data request. A status of 'complete' signals that the requested data is ready to
     * be downloaded from the populated url parameter.
     */
    public static BugsnagEventRequest getEventDataRequest(BugsnagEventRequest originalEventDataRequest) {

        if (ORGANIZATION == null) {
            LOG.error("Required organization is not available. Unable to get event data request");
            return null;
        }

        String endpoint = "/organizations/" + ORGANIZATION.id + "/event_data_requests/" +
            originalEventDataRequest.eventDataRequestId;

        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL, endpoint, null);

        String response = HttpUtils.httpRequest(
            uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            BUGSNAG_HEADERS,
            null);

        return JsonUtils.getPOJOFromJSON(response, BugsnagEventRequest.class);
    }


    /**
     * Get a single organization matching the organization held in the property file.
     */
    private static void setOrganization() {

        URI uri = HttpUtils.buildUri(BUGSNAG_API_URL, "/user/organizations/", null);
        String response = HttpUtils.httpRequest(
            uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            BUGSNAG_HEADERS,
            null);

        List<Organization> organizations = JsonUtils.getPOJOFromJSONAsList(response, Organization.class);

        Organization organization = null;
        for (Organization org : organizations) {
            if (org.name.equalsIgnoreCase(BUGSNAG_ORGANIZATION)) {
                organization = org;
                break;
            }
        }

        if (organization == null) {
            LOG.error("Can not get required organization from Bugsnag matching organization name: "
                + BUGSNAG_ORGANIZATION);
        } else {
            ORGANIZATION = organization;
        }

    }

    /**
     * Get all projects configured under the provided organization id.
     */
    private static void setProjects() {

        if (ORGANIZATION == null) {
            LOG.error("Required organization is not available. Unable to get Bugsnag projects");
            return;
        }

        URI uri = HttpUtils.buildUri(
            BUGSNAG_API_URL,
            "/organizations/" + ORGANIZATION.id + "/projects",
            null);

        String response = HttpUtils.httpRequest(
            uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            BUGSNAG_HEADERS,
            null);

        List<Project> projects = JsonUtils.getPOJOFromJSONAsList(response, Project.class);
        if (projects != null) {
            for (Project project : projects) {
                PROJECTS.put(project.id, project);
            }
        }
    }

    /**
     * Provide a list of Bugsnag projects.
     */
    public static HashMap<String, Project> getProjects() {
        return PROJECTS;
    }

    /**
     * Get requested event data from unique url generated by Bugsnag once the requested data is ready.
     */
    public static List<BugsnagEvent> getEventData(BugsnagEventRequest originalEventDataRequest) {
        URI uri = HttpUtils.buildUri(originalEventDataRequest.url, null, null);

        String response = HttpUtils.httpRequest(
            uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            null,
            null);

        return JsonUtils.getPOJOFromJSONAsList(response, BugsnagEvent.class);
    }
}
