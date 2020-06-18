package org.opentripplanner.middleware;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;

import static org.junit.jupiter.api.Assertions.*;

public class BugsnagTest extends OtpMiddlewareTest {

    @Test
    public void notifyOfBug() {
        assertEquals(BugsnagReporter.get().notify(new RuntimeException("Unit test")), true);
    }

//    private Organization getFirstOrganization() {
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<Organization> organizations = bd.getOrganization();
//        return organizations.get(0);
//    }
//
//    @Test
//    public void getOrganizations() {
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<Organization> organizations = bd.getOrganization();
//        for (Organization organization : organizations) {
//            System.out.println(organization);
//        }
//        assertTrue(organizations.size() > 0);
//    }
//
//    private Project getFirstProject() {
//        Organization organization = getFirstOrganization();
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<Project> projects = bd.getProjects(organization.id);
//        return projects.get(0);
//    }
//
//    @Test
//    public void getProjects() {
//        Organization organization = getFirstOrganization();
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<Project> projects = bd.getProjects(organization.id);
//        for (Project project : projects) {
//            System.out.println(project);
//        }
//        assertTrue(projects.size() > 0);
//    }

//    @Test
//    public void getAllProjectErrors() {
//        Project project = getFirstProject();
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<ProjectError> errors = bd.getAllProjectErrors(project.id);
//        assertTrue(errors.size() > 0);
//    }
//
//    private ProjectError getFirstError() {
//        Project project = getFirstProject();
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<ProjectError> errors = bd.getAllProjectErrors(project.id);
//        return errors.get(0);
//    }
//
//    @Test
//    public void getAllProjectErrorEvents() {
//        ProjectError projectError = getFirstError();
//        BugsnagDispatcher bd = new BugsnagDispatcher();
//        List<EventException> eventExceptions = bd.getAllErrorEvents(projectError.projectId, projectError.id);
//        assertTrue(eventExceptions.size() > 0);
//    }
}
