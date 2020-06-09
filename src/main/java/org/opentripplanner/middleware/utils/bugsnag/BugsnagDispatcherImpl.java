package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

public class BugsnagDispatcherImpl implements BugsnagDispatcher {

    String BUGSNAG_URL = "https://api.bugsnag.com";
    String ORGANIZATION_END_POINT = "/user/organizations/";
    String PROJECT_END_POINT = "/organizations/{organization_Id}/projects";

    String bugsnagUser = getConfigPropertyAsText("BUGSNAG_USER");
    String bugsnagPassword = getConfigPropertyAsText("BUGSNAG_PASSWORD");

    public List<Organization> getOrganization() {

        URI uri = HttpUtils.buildUri(BUGSNAG_URL, ORGANIZATION_END_POINT);
        Organization[] organizations = HttpUtils.callWithBasicAuth(uri, Organization[].class, bugsnagUser, bugsnagPassword);
        return (organizations == null) ? null : Arrays.asList(organizations);
    }

    public List<Project> getProjects(String organizationId) {
        URI uri = HttpUtils.buildUri(BUGSNAG_URL, PROJECT_END_POINT.replace("{organization_Id}", organizationId));
        Project[] projects = HttpUtils.callWithBasicAuth(uri, Project[].class, bugsnagUser, bugsnagPassword);
        return (projects == null) ? null : Arrays.asList(projects);
    }

    public List<Error> getAllProjectErrors(String projectName) {
        return null;
    }

}
