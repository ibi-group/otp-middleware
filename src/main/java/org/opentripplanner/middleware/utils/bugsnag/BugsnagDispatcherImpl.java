package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;
import org.opentripplanner.middleware.utils.bugsnag.response.ProjectError;
import org.opentripplanner.middleware.utils.bugsnag.response.event.EventException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

public class BugsnagDispatcherImpl implements BugsnagDispatcher {

    private final String ORGANIZATION_ID_TOKEN = "{organization_id}";
    private final String PROJECT_ID_TOKEN = "{project_id}";
    private final String ERROR_ID_TOKEN = "{error_id}";

    private final String BUGSNAG_URL = "https://api.bugsnag.com";
    private final String ORGANIZATION_END_POINT = "/user/organizations/";
    private String PROJECT_END_POINT = "/organizations/" + ORGANIZATION_ID_TOKEN + "/projects";
    private String PROJECT_ERROR_END_POINT = "/projects/" + PROJECT_ID_TOKEN + "/errors";
    private String PROJECT_EVENTS_END_POINT = "/projects/" + PROJECT_ID_TOKEN + "/errors/" + ERROR_ID_TOKEN + "/events";

    String bugsnagUser = getConfigPropertyAsText("BUGSNAG_USER");
    String bugsnagPassword = getConfigPropertyAsText("BUGSNAG_PASSWORD");

    public List<Organization> getOrganization() {
        Organization[] organizations = callBugsnagAPI(Organization[].class, ORGANIZATION_END_POINT);
        return (organizations == null) ? new ArrayList<>() : Arrays.asList(organizations);
    }

    public List<Project> getProjects(String organizationId) {
        Project[] projects = callBugsnagAPI(Project[].class, PROJECT_END_POINT.replace(ORGANIZATION_ID_TOKEN, organizationId));
        return (projects == null) ? new ArrayList<>() : Arrays.asList(projects);
    }

    public List<ProjectError> getAllProjectErrors(String projectId) {
        ProjectError[] errors = callBugsnagAPI(ProjectError[].class, PROJECT_ERROR_END_POINT.replace(PROJECT_ID_TOKEN, projectId));
        return (errors == null) ? new ArrayList<>() : Arrays.asList(errors);
    }

    public List<EventException> getAllErrorEvents(String projectId, String errorId) {
        String endpoint = PROJECT_EVENTS_END_POINT.replace(PROJECT_ID_TOKEN, projectId)
            .replace(ERROR_ID_TOKEN, errorId);
        EventException[] errors = callBugsnagAPI(EventException[].class, endpoint);
        return (errors == null) ? new ArrayList<>() : Arrays.asList(errors);
    }

    private <T> T callBugsnagAPI(Class<T> responseClazz, String endpoint) {
        URI uri = HttpUtils.buildUri(BUGSNAG_URL, endpoint);
        return HttpUtils.callWithBasicAuth(uri, responseClazz, bugsnagUser, bugsnagPassword);
    }
}
