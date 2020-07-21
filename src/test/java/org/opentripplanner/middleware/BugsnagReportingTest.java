package org.opentripplanner.middleware;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Bugsnag reporting. All are set to @Disabled because a BUGSNAG_PROJECT_NOTIFIER_API_KEY is required in the
 * test\env.yml file which gets committed. If these tests are required to be run, the README explains how to get a
 * BUGSNAG_PROJECT_NOTIFIER_API_KEY from Bugsnag which can be temporarily saved to test\env.yml.
 */
public class BugsnagReportingTest extends OtpMiddlewareTest {

    @Test @Disabled
    public void createErrorReportWithExceptionContextAndMessage() {
        assertTrue(BugsnagReporter.reportErrorToBugsnag("This is the error context",
            "This is the message unique to this error event",
            new Exception("This is the exception that occurred")));
    }

    @Test @Disabled
    public void createErrorReportWithMessageAndException() {
        assertTrue(BugsnagReporter.reportErrorToBugsnag(null,
            "Unit test 2 message",
            new Exception("Unit test exception 2")));
    }

    @Test @Disabled
    public void createErrorReportWithContextAndException() {
        assertTrue(BugsnagReporter.reportErrorToBugsnag("Unit test 3 context",
            null,
            new Exception("Unit test exception 3")));
    }

    @Test @Disabled
    public void createErrorReportWithNullTripRequest() {
        TripRequest t = null;
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> Persistence.tripRequests.create(t));
        assertEquals("document can not be null", exception.getMessage());
    }
}
