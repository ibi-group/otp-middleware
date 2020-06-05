package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;

import java.util.List;

public interface BugsnagDispatcher {
    Organization getOrganization();
    List<Project> getProjects();
    List<Error> getAllProjectErrors(String projectName);
}
