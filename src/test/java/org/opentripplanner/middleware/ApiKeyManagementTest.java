package org.opentripplanner.middleware;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for creating and deleting api keys. The following config parameters are required for these tests to run:
 *
 * DISABLE_AUTH set to true to bypass auth checks.
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 * OTP_MIDDLEWARE_URI_ROOT, e.g. http://localhost:4567.
 */
// FIXME: User is derived from token so difficult to test. To be addressed at a later date.
public class ApiKeyManagementTest extends OtpMiddlewareTest {

    @Test @Disabled
    public void createApiKeyForUser() {
    }

    @Test @Disabled
    public void getApiKeyForUser() {
    }

    @Test @Disabled
    public void deleteApiKeyForUser() {
    }
}
