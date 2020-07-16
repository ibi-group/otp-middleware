package org.opentripplanner.middleware;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;

/**
 * Tests for Bugsnag reporting. All are set to @Disabled because a BUGSNAG_PROJECT_NOTIFIER_API_KEY is required in the
 * test\env.yml file which gets committed. If these tests are required to be run, the README explains how to get a
 * BUGSNAG_PROJECT_NOTIFIER_API_KEY from Bugsnag which can be temporarily saved to test\env.yml.
 */
public class BugsnagReportingTest extends OtpMiddlewareTest {

    @Test @Disabled
    public void createErrorReportWithExceptionAndMessage() {
        BugsnagReporter.reportErrorToBugsnag(new Exception("Unit test exception 1"), "Unit test 1");
    }

    @Test @Disabled
    public void createErrorReportWithException() {
        BugsnagReporter.reportErrorToBugsnag(new Exception("Unit test exception 2"), null);
    }
}
