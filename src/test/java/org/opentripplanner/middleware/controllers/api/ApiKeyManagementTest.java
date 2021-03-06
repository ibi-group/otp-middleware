package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.CreateApiKeyException;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.DEFAULT_USAGE_PLAN_ID;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedDelete;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;

/**
 * Tests for creating and deleting api keys. The following config parameters must be set in
 * configurations/default/env.yml for these end-to-end tests to run:
 * - RUN_E2E=true the end-to-end environment variable must be set (NOTE: this is not a config value)
 * - An AWS_PROFILE is required, or AWS access has been configured for your operating environment. E.g.,
 *      C:\Users\<username>\.aws\credentials in Windows or Mac OS equivalent.
 * - DISABLE_AUTH set to true to bypass auth checks and use users defined here.
 * - DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyManagementTest.class);
    private static ApiUser apiUser;
    private static AdminUser adminUser;

    /**
     * Create an {@link ApiUser} and an {@link AdminUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws IOException {
        assumeTrue(IS_END_TO_END);
        // TODO: It might be useful to allow this to run without DISABLE_AUTH set to true (in an end-to-end environment
        //  using real tokens from Auth0.
        setAuthDisabled(true);
        apiUser = PersistenceTestUtils.createApiUser("test@example.com");
        adminUser = PersistenceTestUtils.createAdminUser("test@example.com");
    }

    /**
     * Remove the users {@link AdminUser} and {@link ApiUser} and any remaining API keys.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(IS_END_TO_END);
        // refresh API key(s)
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        apiUser.delete();
        Persistence.adminUsers.removeById(adminUser.id);
        restoreDefaultAuthDisabled();
    }

    /**
     * Ensure that an {@link ApiUser} can create an API key for self.
     */
    @Test
    public void canCreateApiKeyForSelf() throws Exception {
        HttpResponseValues response = createApiKeyRequest(apiUser.id, apiUser);
        assertEquals(HttpStatus.OK_200, response.status);
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.responseBody, ApiUser.class);
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("API user successfully created API key id {}", userFromResponse.apiKeys.get(0).keyId);
        assertEquals(userFromDb.apiKeys, userFromResponse.apiKeys);
    }

    /**
     * Ensure that an {@link AdminUser} can create an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanCreateApiKeyForApiUser() throws Exception {
        HttpResponseValues response = createApiKeyRequest(apiUser.id, adminUser);
        assertEquals(HttpStatus.OK_200, response.status);
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.responseBody, ApiUser.class);
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("Admin user successfully created API key id {}", userFromResponse.apiKeys.get(0).keyId);
        assertEquals(userFromDb.apiKeys, userFromResponse.apiKeys);
    }

    /**
     * Ensure that an {@link ApiUser} is unable to delete an API key for self (to prevent delete/create abuse of request
     * limits).
     */
    @Test
    public void cannotDeleteApiKeyForSelf() throws Exception {
        ensureApiKeyExists();
        int initialKeyCount = apiUser.apiKeys.size();
        // delete key
        String keyId = apiUser.apiKeys.get(0).keyId;
        HttpResponseValues response = deleteApiKeyRequest(apiUser.id, keyId, apiUser);
        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
        LOG.info("Delete key request status: {}", response.status);
        // Ensure key count is the same after delete request.
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        assertEquals(initialKeyCount, userFromDb.apiKeys.size());
    }

    /**
     * Ensure that an {@link AdminUser} can delete an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanDeleteApiKeyForApiUser() throws Exception {
        ensureApiKeyExists();
        // delete key
        String keyId = apiUser.apiKeys.get(0).keyId;
        HttpResponseValues response = deleteApiKeyRequest(apiUser.id, keyId, adminUser);
        assertEquals(HttpStatus.OK_200, response.status);
        ApiUser userFromResponse = JsonUtils.getPOJOFromJSON(response.responseBody, ApiUser.class);
        assertTrue(userFromResponse.apiKeys.isEmpty());
        // refresh API key
        ApiUser userFromDb = Persistence.apiUsers.getById(apiUser.id);
        LOG.info("Admin user successfully deleted API key id {}", keyId);
        assertTrue(userFromDb.apiKeys.isEmpty());
    }

    /**
     * Make sure that at least one API key exists.
     */
    private boolean ensureApiKeyExists() {
        // Refresh API keys.
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        if (apiUser.apiKeys.isEmpty()) {
            // Create key if there are none.
            try {
                apiUser.createApiKey(DEFAULT_USAGE_PLAN_ID, true);
                LOG.info("Successfully created API key");
                return true;
            } catch (CreateApiKeyException e) {
                LOG.error("Could not create API key", e);
                return false;
            }
        }
        return true;
    }

    /**
     * Create API key for target user based on authorization of requesting user
     */
    private HttpResponseValues createApiKeyRequest(String targetUserId, AbstractUser requestingUser) throws Exception {
        String path = String.format("api/secure/application/%s/apikey", targetUserId);
        return mockAuthenticatedRequest(path, "", requestingUser, HttpMethod.POST);
    }

    /**
     * Delete API key for target user based on authorization of requesting user
     */
    private static HttpResponseValues deleteApiKeyRequest(String targetUserId, String apiKeyId, AbstractUser requestingUser)
        throws Exception {
        String path = String.format("api/secure/application/%s/apikey/%s", targetUserId, apiKeyId);
        return mockAuthenticatedDelete(path, requestingUser);
    }
}
