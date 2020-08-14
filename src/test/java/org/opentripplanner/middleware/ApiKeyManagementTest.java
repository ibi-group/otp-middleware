package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsText;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;

/**
 * Tests for creating and deleting api keys. The following config parameters are required for these tests to run:
 *
 * An AWS_PROFILE is required, or AWS access has been configured for your operating environment e.g.
 * C:\Users\<username>\.aws\credentials in Windows or Mac OS equivalent.
 *
 * DISABLE_AUTH set to true to bypass auth checks and use users defined here. DEFAULT_USAGE_PLAN_ID set to a valid usage
 * plan id. AWS requires this to create an api key.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTest {

    private static String DEFAULT_USAGE_PLAN_ID;
    private static ApiUser apiUser;
    private static AdminUser adminUser;

    /**
     * Create an {@link ApiUser} and an {@link AdminUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws IOException {
        OtpMiddlewareTest.setUp();
        DEFAULT_USAGE_PLAN_ID = getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");
        apiUser = PersistenceUtil.createApiUser("test@example.com");
        adminUser = PersistenceUtil.createAdminUser("test@example.com");
    }

    /**
     * Remove the users {@link AdminUser} and {@link ApiUser} and any remaining API keys.
     */
    @AfterAll
    public static void tearDown() {
        Persistence.adminUsers.removeById(adminUser.id);

        // refresh api keys
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        for (ApiKey apiKey : apiUser.apiKeys) {
            ApiGatewayUtils.deleteApiKey(apiKey);
        }

        Persistence.apiUsers.removeById(apiUser.id);
    }

    /**
     * Ensure that an {@link ApiUser} can create an API key for self.
     */
    @Test
    public void canCreateApiKeyForSelf() {
        HttpResponse<String> response = createApiKey(apiUser.id, apiUser.auth0UserId);
        Assertions.assertEquals(response.statusCode(), 200);
        ApiUser testUser = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        // refresh API key
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        //TODO: Convert to Lambda?
        Assertions.assertTrue(apiUser.apiKeys.equals(testUser.apiKeys));
    }

    /**
     * Ensure that an {@link AdminUser} can create an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanCreateApiKeyForApiUser() {
        HttpResponse<String> response = createApiKey(apiUser.id, adminUser.auth0UserId);
        Assertions.assertEquals(response.statusCode(), 200);
        ApiUser testUser = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        // refresh API key
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        //TODO: Convert to Lambda?
        Assertions.assertTrue(apiUser.apiKeys.equals(testUser.apiKeys));
    }

    /**
     * Ensure that an {@link ApiUser} can delete an API key for self.
     */
    @Test
    public void canDeleteApiKeyForSelf() {
        ensureAtLeastOneApiKeyIsAvailable();
        // delete key
        HttpResponse<String> response = deleteApiKey(apiUser.id, apiUser.apiKeys.get(0).id, apiUser.auth0UserId);
        Assertions.assertEquals(response.statusCode(), 200);
        ApiUser testUser = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        Assertions.assertTrue(testUser.apiKeys.isEmpty());
        // refresh API key
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        Assertions.assertTrue(apiUser.apiKeys.isEmpty());
    }

    /**
     * Ensure that an {@link AdminUser} can delete an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanDeleteApiKeyForApiUser() {
        ensureAtLeastOneApiKeyIsAvailable();
        // delete key
        HttpResponse<String> response = deleteApiKey(apiUser.id, apiUser.apiKeys.get(0).id, adminUser.auth0UserId);
        Assertions.assertEquals(response.statusCode(), 200);
        ApiUser testUser = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        Assertions.assertTrue(testUser.apiKeys.isEmpty());
        // refresh API key
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        Assertions.assertTrue(apiUser.apiKeys.isEmpty());
    }

    /**
     * Make sure that at least one API key exists.
     */
    private void ensureAtLeastOneApiKeyIsAvailable() {
        // Refresh API keys.
        apiUser = Persistence.apiUsers.getById(apiUser.id);

        if (apiUser.apiKeys.isEmpty()) {
            ApiKey apiKey = new ApiKey(ApiGatewayUtils.createApiKey(apiUser.id, DEFAULT_USAGE_PLAN_ID));
            apiUser.apiKeys.add(apiKey);
            // Save update so the API key delete endpoint is aware of the new API key.
            Persistence.apiUsers.replace(apiUser.id, apiUser);
        }
    }

    /**
     * Create API key for target user based on authorization of requesting user
     */
    private HttpResponse<String> createApiKey(String targetUserId, String requestingAuth0UserId) {
        String url = String.format("api/secure/application/%s/apikey", targetUserId);
        return mockAuthenticatedRequest(url, HttpUtils.REQUEST_METHOD.POST, requestingAuth0UserId);
    }

    /**
     * Delete API key for target user based on authorization of requesting user
     */
    private HttpResponse<String> deleteApiKey(String targetUserId, String apiKeyId, String requestingAuth0UserId) {
        String url = String.format("api/secure/application/%s/apikey/%s", targetUserId, apiKeyId);
        return mockAuthenticatedRequest(url, HttpUtils.REQUEST_METHOD.DELETE, requestingAuth0UserId);
    }
}
