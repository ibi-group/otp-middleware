package org.opentripplanner.middleware;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcherImpl;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BugsnagTest extends OtpMiddlewareTest {

    @Test
    public void notifyOfBug() {
        assertEquals(BugsnagReporter.get().notify(new RuntimeException("Unit test")), true);
    }

    @Test
    public void getOrganization() {
        BugsnagDispatcher bd = new BugsnagDispatcherImpl();
        Organization organization = bd.getOrganization();
        assertNotNull(organization);
    }

}
