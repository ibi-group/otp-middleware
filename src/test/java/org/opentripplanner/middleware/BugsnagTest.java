package org.opentripplanner.middleware;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcherImpl;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertNotNull(organizations);
    }

    @Test
    public void getProjects() {
        Organization organization = getFirstOrganization();
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        List<Project> projects = bd.getProjects(organization.id);
        for (Project project : projects) {
            System.out.println(project);
        }
        assertNotNull(projects);
    }

}
