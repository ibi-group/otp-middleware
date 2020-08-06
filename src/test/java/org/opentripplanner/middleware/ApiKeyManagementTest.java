package org.opentripplanner.middleware;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;

/**
 * Tests for creating and deleting api keys. The following config parameters are required for these tests to run:
 *
 * DISABLE_AUTH set to true to bypass auth checks.
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 * OTP_MIDDLEWARE_URI_ROOT, e.g. http://localhost:4567.
 */
// FIXME: User is derived from token so difficult to test. To be addressed at a later date.
public class ApiKeyManagementTest extends OtpMiddlewareTest {

    private static ApiUser apiUser;

    @BeforeAll
    public static void setUp() throws IOException {
        OtpMiddlewareTest.setUp();
        apiUser = PersistenceUtil.createApiUser("test@example.com");
    }

    /**
     * Ensure that an {@link ApiUser} can create an API key for self.
     */
    @Test
    public void canCreateApiKeyForSelf() {
        // TODO: Call endpoint to create API key
        HttpResponse<String> stringHttpResponse = HttpUtils.httpRequestRawResponse(
            URI.create("http://localhost:4567/api/secure/application/apikey"),
            1000,
            HttpUtils.REQUEST_METHOD.POST,
            new HashMap<>(),
            null
        );
        Assertions.assertEquals(stringHttpResponse.statusCode(), 200);
    }

    /**
     * Ensure that an {@link AdminUser} can create an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanCreateApiKeyForApiUser() {
        // TODO: Call endpoint to create API key
    }

    @Test
    public void canGetApiKeyForUser() {
        // TODO: Call endpoint to get API key with value
    }

    @Test
    public void canDeleteApiKeyForUser() {
        // TODO: Call endpoint to delete API key by ID.
    }
}
