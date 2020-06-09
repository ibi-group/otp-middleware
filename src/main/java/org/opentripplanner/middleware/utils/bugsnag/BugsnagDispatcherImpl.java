package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;
import org.opentripplanner.middleware.utils.bugsnag.response.ProjectError;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

public class BugsnagDispatcherImpl implements BugsnagDispatcher {

    String ORGANIZATION_ID_TOKEN = "{organization_id}";
    String PROJECT_ID_TOKEN = "{project_id}";
    String BUGSNAG_URL = "https://api.bugsnag.com";
    String ORGANIZATION_END_POINT = "/user/organizations/";
    String PROJECT_END_POINT = "/organizations/" + ORGANIZATION_ID_TOKEN + "/projects";
    String PROJECT_ERROR_END_POINT = "/projects/" + PROJECT_ID_TOKEN + "/errors";

    String bugsnagUser = getConfigPropertyAsText("BUGSNAG_USER");
    String bugsnagPassword = getConfigPropertyAsText("BUGSNAG_PASSWORD");

    public List<Organization> getOrganization() {
        URI uri = HttpUtils.buildUri(BUGSNAG_URL, ORGANIZATION_END_POINT);
        Organization[] organizations = HttpUtils.callWithBasicAuth(uri, Organization[].class, bugsnagUser, bugsnagPassword);
        return (organizations == null) ? new ArrayList<>() : Arrays.asList(organizations);
    }

    public List<Project> getProjects(String organizationId) {
        URI uri = HttpUtils.buildUri(BUGSNAG_URL, PROJECT_END_POINT.replace(ORGANIZATION_ID_TOKEN, organizationId));
        Project[] projects = HttpUtils.callWithBasicAuth(uri, Project[].class, bugsnagUser, bugsnagPassword);
        return (projects == null) ? new ArrayList<>() : Arrays.asList(projects);
    }

    public List<ProjectError> getAllProjectErrors(String projectId) {
        URI uri = HttpUtils.buildUri(BUGSNAG_URL, PROJECT_ERROR_END_POINT.replace(PROJECT_ID_TOKEN, projectId));
        ProjectError[] errors = HttpUtils.callWithBasicAuth(uri, ProjectError[].class, bugsnagUser, bugsnagPassword);
        return (errors == null) ? new ArrayList<>() : Arrays.asList(errors);
    }
}
