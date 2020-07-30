package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.controllers.api.ApiKeyManagementController;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createApiUser;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Tests for creating/deleting and getting usage logs for an api key. The tests are required to be run within a single
 * unit test to guarantee the order of tasks (create, check usage, delete). The following config parameters are
 * required for these tests to run:
 *
 * DISABLE_AUTH set to true to bypass auth checks on '/admin'.
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 * OTP_MIDDLEWARE_URI_ROOT, e.g. http://localhost:4567.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTest {

    private static final String OTP_MIDDLEWARE_URI_ROOT
        = getConfigPropertyAsText("OTP_MIDDLEWARE_URI_ROOT");

    private static ApiUser user;

    private static final int CONNECTION_TIMEOUT_IN_SECONDS = 5;

    @BeforeAll @Disabled
    public static void setup() {
        String email = "test@example.com";
        user = createApiUser(email);
    }

    @AfterAll @Disabled
    public static void tearDown() {
        Persistence.apiUsers.removeById(user.id);
    }

    // FIXME Perhaps direct calls to ApiGatewayUtils would be better?
    @Test @Disabled
    public void manageApiKeyForUser() {
        // create api key for user
        URI uri = HttpUtils.buildUri(OTP_MIDDLEWARE_URI_ROOT,
            ApiKeyManagementController.createApiKeyEndpoint,
            String.format("userId=%s", user.id));
        HttpResponse<String> response = HttpUtils.httpRequestRawResponse(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            null,
            null);
        assertEquals(response.statusCode(), 200);

        ApiUser updatedUser = Persistence.apiUsers.getById(user.id);
        assertFalse(updatedUser.apiKeyIds.isEmpty());

        // get api key usage
        uri = HttpUtils.buildUri(OTP_MIDDLEWARE_URI_ROOT,
            ApiKeyManagementController.usageApiKeyEndpoint,
            String.format("userId=%s", user.id));
        response = HttpUtils.httpRequestRawResponse(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            null,
            null);
        assertEquals(response.statusCode(), 200);

        // delete api key for user
        uri = HttpUtils.buildUri(OTP_MIDDLEWARE_URI_ROOT,
            ApiKeyManagementController.deleteApiKeyEndpoint,
            String.format("userId=%s&apiKeyId=%s", user.id, updatedUser.apiKeyIds.get(0)));
        response = HttpUtils.httpRequestRawResponse(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.GET,
            null,
            null);
        assertEquals(response.statusCode(), 200);

        updatedUser = Persistence.apiUsers.getById(user.id);
        assertTrue(updatedUser.apiKeyIds.isEmpty());
    }
}
