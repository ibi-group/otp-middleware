package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;
import org.opentripplanner.middleware.utils.bugsnag.response.ProjectError;
import org.opentripplanner.middleware.utils.bugsnag.response.event.EventException;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Class to provide Bugsnag information bu constructing required API calls to Bugsnag
 */
public class BugsnagDispatcherImpl implements BugsnagDispatcher {

    private final String BUGSNAG_API_KEY = getConfigPropertyAsText("BUGSNAG_API_KEY");
    private final String BUGSNAG_ORGANIZATION = getConfigPropertyAsText("BUGSNAG_ORGANIZATION");
    // TODO use getConfigPropertyAsInt once all branches are merged
    private final String BUGSNAG_REPORTING_WINDOW_IN_DAYS = getConfigPropertyAsText("BUGSNAG_REPORTING_WINDOW_IN_DAYS");

    /**
     * Get all organisations from Bugsnag
     */
    public List<Organization> getOrganization() {
        final String endpoint = "/user/organizations/";

        Organization[] organizations = callAPI(Organization[].class, endpoint, null);
        return (organizations == null) ? new ArrayList<>() : Arrays.asList(organizations);
    }

    /**
     * Get a single organisation matching the provided organization
     */
    private Organization getOrganization(String organizationName) {
        Organization organization = null;
        List<Organization> organizations = getOrganization();
        for (Organization org : organizations) {
            if (org.name.equalsIgnoreCase(organizationName)) {
                organization = org;
                break;
            }
        }
        return organization;
    }

    /**
     * Get all projects configured under the provided organization id
     */
    public List<Project> getProjects(String organizationId) {
        String endpoint = "/organizations/" + organizationId + "/projects";

        Project[] projects = callAPI(Project[].class, endpoint, null);
        return (projects == null) ? new ArrayList<>() : Arrays.asList(projects);
    }

    /**
     * Get all projects configured for an organization as a HashMap
     */
    private HashMap<String, Project> getProjectsAsMap(String organizationId) {

        HashMap<String, Project> projectMap = new HashMap<>();
        List<Project> projects = getProjects(organizationId);
        if (projects == null) {
            return projectMap;
        }

        for (Project project : projects) {
            projectMap.put(project.id, project);
        }

        return projectMap;
    }


    /**
     * Get all project errors for the provided project id
     */
    public List<ProjectError> getAllProjectErrors(String projectId) {
        String endpoint = "/projects/" + projectId + "/errors";

        ProjectError[] projectErrors = callAPI(ProjectError[].class, endpoint, null);
        return (projectErrors == null) ? new ArrayList<>() : Arrays.asList(projectErrors);
    }


    public List<ProjectError> getProjectErrorsWithinDateRange(String projectId) {
        String endpoint = "/projects/" + projectId + "/errors";

        String queryParams = buildFilterByDateRange(Integer.parseInt(BUGSNAG_REPORTING_WINDOW_IN_DAYS));
        ProjectError[] projectErrors = callAPI(ProjectError[].class, endpoint, queryParams);
        return (projectErrors == null) ? new ArrayList<>() : Arrays.asList(projectErrors);
    }

    /**
     * Get all error events for a project and error
     */
    public List<EventException> getAllErrorEvents(String projectId, String errorId) {
        String endpoint = "/projects/" + projectId + "/errors/" + errorId + "/events";

        EventException[] errorEvents = callAPI(EventException[].class, endpoint, null);
        return (errorEvents == null) ? new ArrayList<>() : Arrays.asList(errorEvents);
    }

    /**
     * Get all project errors across all projects within the reporting window
     */
    private List<ProjectError> getAllProjectErrorsWithinReportingWindow() {
        Organization organization = getOrganization(BUGSNAG_ORGANIZATION);
        List<Project> projects = getProjects(organization.id);

        List<ProjectError> allProjectErrors = new ArrayList<>();
        for (Project project : projects) {
            List<ProjectError> projectErrors = getProjectErrorsWithinDateRange(project.id);
            allProjectErrors.addAll(projectErrors);
        }

        return allProjectErrors;
    }

    /**
     * Get the total number of errors across all projects within the reporting window
     */
    public int getTotalNumOfErrorsWithinReportingWindow() {
        List<ProjectError> allProjectErrors = getAllProjectErrorsWithinReportingWindow();
        return allProjectErrors.size();
    }

    /**
     * Get all errors across all projects based on the default organization and within the reporting window
     */
    public List<ErrorSummary> getErrorSummary() {

        Organization organization = getOrganization(BUGSNAG_ORGANIZATION);
        HashMap<String, Project> projects = getProjectsAsMap(organization.id);
        List<ProjectError> projectErrors = getAllProjectErrorsWithinReportingWindow();

        List<ErrorSummary> errorSummaries = new ArrayList<>();
        for (ProjectError projectError : projectErrors) {
            errorSummaries.add(new ErrorSummary(projects.get(projectError.projectId), projectError));
        }

        return errorSummaries;
    }

    /**
     * Build filter parameters based on last number of days provided
     */
    private String buildFilterByDateRange(int lastNumOfDays) {
        StringBuilder sb = new StringBuilder();
        sb.append("filters[event.since][][value]=")
            .append(lastNumOfDays)
            // following 'd' is very important
            .append("d&filters[event.since][][type]=eq");

        return sb.toString();
    }

    /**
     * Makes a call to a specific Bugsnag API endpoint
     */
    private <T> T callAPI(Class<T> responseClazz, String endpoint, String queryParams) {
        URI uri = HttpUtils.buildUri("https://api.bugsnag.com", endpoint, queryParams);
        return HttpUtils.callWithAuthToken(uri, responseClazz, BUGSNAG_API_KEY);
    }
}
