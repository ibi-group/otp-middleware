package org.opentripplanner.middleware.bugsnag;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Bugsnag reporting. All are set to @Disabled because a BUGSNAG_PROJECT_NOTIFIER_API_KEY is required in the
 * test\env.yml file which gets committed. If these tests are required to be run, the README explains how to get a
 * BUGSNAG_PROJECT_NOTIFIER_API_KEY from Bugsnag which can be temporarily saved to test\env.yml.
 */
public class BugsnagReporterTest {

    @Test @Disabled
    public void createErrorReportWithExceptionContextAndMessage() {
        assertTrue(BugsnagReporter.reportErrorToBugsnag("This is the error context",
            "This is the message unique to this error event",
            new Exception("This is the exception that occurred")));
    }

    @Test @Disabled
    public void createErrorReportWithMessageAndException() {
        boolean success = BugsnagReporter.reportErrorToBugsnag(
            null,
            "Unit test 2 message",
            new Exception("Unit test exception 2"));
        assertTrue(success);
    }

    @Test @Disabled
    public void createErrorReportWithContextAndException() {
        boolean success = BugsnagReporter.reportErrorToBugsnag(
            "Unit test 3 context",
            new Exception("Unit test exception 3")
        );
        assertTrue(success);
    }

    @Test @Disabled
    public void createErrorReportWithNullTripRequest() {
        TripRequest badRequest = null;
        assertFalse(Persistence.tripRequests.create(badRequest));
    }
}
