package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.io.IOException;

import static org.opentripplanner.middleware.persistence.PersistenceUtil.getApiUser;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.getStatusCodeFromResponse;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.removeAdminUser;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.removeApiUser;

/**
 * Tests for creating and deleting api keys. The following config parameters are required for these tests to run:
 *
 * DISABLE_AUTH set to true to bypass auth checks and use users defined here.
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTest {

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
        removeAdminUser(adminUser.id);

        // refresh api keys
        apiUser = getApiUser(apiUser.id);
        for (ApiKey apiKey : apiUser.apiKeys) {
            // delete api keys if present
            String url = String.format("http://localhost:4567/api/secure/application/%s/apikey/%s?auth0UserId=%s",
                apiUser.id,
                apiKey.id,
                apiUser.auth0UserId);
            getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.DELETE);
        }

        removeApiUser(apiUser.id);
    }

    /**
     * Ensure that an {@link ApiUser} can create an API key for self.
     */
    @Test
    public void canCreateApiKeyForSelf() {
        String url = String.format("http://localhost:4567/api/secure/application/%s/apikey?auth0UserId=%s",
            apiUser.id,
            apiUser.auth0UserId);
        Assertions.assertEquals(getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.POST), 200);
    }

    /**
     * Ensure that an {@link AdminUser} can create an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanCreateApiKeyForApiUser() {
        String url = String.format("http://localhost:4567/api/secure/application/%s/apikey?auth0UserId=%s",
            apiUser.id,
            adminUser.auth0UserId);
        Assertions.assertEquals(getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.POST), 200);
    }

    /**
     * Ensure that an {@link ApiUser} can delete an API key for self.
     */
    @Test
    public void canDeleteApiKeyForSelf() {

        ensureAtLeastOneApiKeyIsAvailable();

        // delete key
        String url = String.format("http://localhost:4567/api/secure/application/%s/apikey/%s?auth0UserId=%s",
            apiUser.id,
            apiUser.apiKeys.get(0).id,
            apiUser.auth0UserId);
        Assertions.assertEquals(getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.DELETE), 200);
    }

    /**
     * Ensure that an {@link AdminUser} can delete an API key for an {@link ApiUser}.
     */
    @Test
    public void adminCanDeleteApiKeyForApiUser() {

        ensureAtLeastOneApiKeyIsAvailable();

        // delete key
        String url = String.format("http://localhost:4567/api/secure/application/%s/apikey/%s?auth0UserId=%s",
            apiUser.id,
            apiUser.apiKeys.get(0).id,
            adminUser.auth0UserId);
        Assertions.assertEquals(getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.DELETE), 200);
    }

    /**
     * Make sure that at least one API key exists.
     */
    private void ensureAtLeastOneApiKeyIsAvailable() {
        // refresh api keys
        apiUser = getApiUser(apiUser.id);

        if (apiUser.apiKeys.isEmpty()) {
            // create key
            String url = String.format("http://localhost:4567/api/secure/application/%s/apikey?auth0UserId=%s",
                apiUser.id,
                adminUser.auth0UserId);
            getStatusCodeFromResponse(url, HttpUtils.REQUEST_METHOD.POST);

            // refresh api keys
            apiUser = getApiUser(apiUser.id);
        }

    }
}
