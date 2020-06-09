package org.opentripplanner.middleware;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcherImpl;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;
import org.opentripplanner.middleware.utils.bugsnag.response.ProjectError;
import org.opentripplanner.middleware.utils.bugsnag.response.event.EventException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BugsnagTest extends OtpMiddlewareTest {

    @Test
    public void notifyOfBug() {
        assertEquals(BugsnagReporter.get().notify(new RuntimeException("Unit test")), true);
    }

    private Organization getFirstOrganization() {
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<Organization> organizations = bd.getOrganization();
        return organizations.get(0);
    }

    @Test
    public void getOrganizations() {
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<Organization> organizations = bd.getOrganization();
        for (Organization organization : organizations) {
            System.out.println(organization);
        }
        assertTrue(organizations.size() > 0);
    }

    private Project getFirstProject() {
        Organization organization = getFirstOrganization();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<Project> projects = bd.getProjects(organization.id);
        return projects.get(0);
    }

    @Test
    public void getProjects() {
        Organization organization = getFirstOrganization();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<Project> projects = bd.getProjects(organization.id);
        for (Project project : projects) {
            System.out.println(project);
        }
        assertTrue(projects.size() > 0);
    }

    @Test
    public void getAllProjectErrors() {
        Project project = getFirstProject();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<ProjectError> errors = bd.getAllProjectErrors(project.id);
        assertTrue(errors.size() > 0);
    }

    private ProjectError getFirstError() {
        Project project = getFirstProject();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<ProjectError> errors = bd.getAllProjectErrors(project.id);
        return errors.get(0);
    }

    @Test
    public void getAllProjectErrorEvents() {
        ProjectError projectError = getFirstError();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<EventException> eventExceptions = bd.getAllErrorEvents(projectError.projectId, projectError.id);
        assertTrue(eventExceptions.size() > 0);
    }
}
