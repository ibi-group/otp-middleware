package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsText;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.DEFAULT_USAGE_PLAN_ID;

/**
 * Tests for creating and deleting api keys. The following config parameters are must be set in
 * configurations/default/env.yml for these end-to-end tests to run:
 *  - RUN_E2E=true the end-to-end environment variable must be set (NOTE: this is not a config value)
 *  - An AWS_PROFILE is required, or AWS access has been configured for your operating environment e.g.
 *    C:\Users\<username>\.aws\credentials in Windows or Mac OS equivalent.
 *  - DISABLE_AUTH set to true to bypass auth checks and use users defined here. DEFAULT_USAGE_PLAN_ID set to a valid usage
 *    plan id. AWS requires this to create an api key.
 *  - TODO: It might be useful to allow this to run without DISABLE_AUTH set to true (in an end-to-end environment using
 *      real tokens from Auth0.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyManagementTest.class);
    private static ApiUser apiUser;
    private static AdminUser adminUser;

    /**
     * Create an {@link ApiUser} and an {@link AdminUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws IOException {
        OtpMiddlewareTest.setUp();
        apiUser = PersistenceUtil.createApiUser("test@example.com");
        adminUser = PersistenceUtil.createAdminUser("test@example.com");
    }

    /**
     * Remove the users {@link AdminUser} and {@link ApiUser} and any remaining API keys.
     */
    @AfterAll
    public static void tearDown() {
        // Delete admin user.
        Persistence.adminUsers.removeById(adminUser.id);
        // Refresh api keys for user.
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        apiUser.delete();
    }

    /**
     * Ensure that an {@link ApiUser} can create an API key for self.
     */
    @Test
    public void canCreateApiKeyForSelf() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        HttpResponse<String> response = createApiKeyRequest(apiUser.id, apiUser);
        Assertions.assertEquals(200, response.statusCode());
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("API user successfully created API key id {}", userFromResponse.apiKeys.get(0).id);
        assertEquals(userFromDb.apiKeys, userFromResponse.apiKeys);
    }

    /**
     * Ensure that an {@link AdminUser} can create an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanCreateApiKeyForApiUser() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        HttpResponse<String> response = createApiKeyRequest(apiUser.id, adminUser);
        Assertions.assertEquals(200, response.statusCode());
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("Admin user successfully created API key id {}", userFromResponse.apiKeys.get(0).id);
        assertEquals(userFromDb.apiKeys, userFromResponse.apiKeys);
    }

    /**
     * Ensure that an {@link ApiUser} can delete an API key for self.
     */
    @Test
    public void canDeleteApiKeyForSelf() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        ensureAtLeastOneApiKeyIsAvailable();
        // delete key
        String keyId = apiUser.apiKeys.get(0).id;
        HttpResponse<String> response = deleteApiKeyRequest(apiUser.id, keyId, apiUser);
        Assertions.assertEquals(200, response.statusCode());
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        assertTrue(userFromResponse.apiKeys.isEmpty());
        LOG.info("API user successfully deleted API key id {}", keyId);
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        assertTrue(userFromDb.apiKeys.isEmpty());
    }

    /**
     * Ensure that an {@link AdminUser} can delete an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanDeleteApiKeyForApiUser() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        ensureAtLeastOneApiKeyIsAvailable();
        // delete key
        String keyId = apiUser.apiKeys.get(0).id;
        HttpResponse<String> response = deleteApiKeyRequest(apiUser.id, keyId, adminUser);
        Assertions.assertEquals(response.statusCode(), 200);
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.body(), ApiUser.class);
        assertTrue(userFromResponse.apiKeys.isEmpty());
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("Admin user successfully deleted API key id {}", keyId);
        assertTrue(userFromDb.apiKeys.isEmpty());
    }

    /**
     * Make sure that at least one API key exists.
     */
    private void ensureAtLeastOneApiKeyIsAvailable() {
        // Refresh API keys.
        apiUser = Persistence.apiUsers.getById(apiUser.id);

        if (apiUser.apiKeys.isEmpty()) {
            apiUser.createApiKey(DEFAULT_USAGE_PLAN_ID, true);
        }
    }

    /**
     * Create API key for target user based on authorization of requesting user
     */
    private HttpResponse<String> createApiKeyRequest(String targetUserId, AbstractUser requestingUser) {
        String path = String.format("api/secure/application/%s/apikey", targetUserId);
        return mockAuthenticatedRequest(path, HttpUtils.REQUEST_METHOD.POST, requestingUser);
    }

    /**
     * Delete API key for target user based on authorization of requesting user
     */
    private HttpResponse<String> deleteApiKeyRequest(String targetUserId, String apiKeyId, AbstractUser requestingUser) {
        String path = String.format("api/secure/application/%s/apikey/%s", targetUserId, apiKeyId);
        return mockAuthenticatedRequest(path, HttpUtils.REQUEST_METHOD.DELETE, requestingUser);
    }
}
