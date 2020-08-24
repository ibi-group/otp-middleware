package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedPost;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.auth.Auth0Users.createAuth0UserForEmail;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.DEFAULT_USAGE_PLAN_ID;

public class ApiUserFlowTest {
    private static ApiUser apiUser;
    private static OtpUser otpUser;

    /**
     * Create an {@link ApiUser} and an {@link AdminUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws IOException {
        OtpMiddlewareTest.setUp();
        // As a pre-condition, create an API User with API key.
        apiUser = PersistenceUtil.createApiUser(String.format("test-%s@example.com", UUID.randomUUID().toString()));
        apiUser.createApiKey(DEFAULT_USAGE_PLAN_ID, true);
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        if (apiUser != null) apiUser.delete();
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete();
    }

    @Test
    public void canSimulateApiUserFlow() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));

        try {
            String password = System.getenv("password");
            createAuth0UserForEmail(apiUser.email, password);
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
        // First, simulate some HTTP requests made with AWS API key.
        OtpUser otpUser = new OtpUser();
        otpUser.hasConsentedToTerms = true;
        otpUser.email = "test_user@example.com";
        otpUser.auth0UserId = "test_id";
        HttpResponse<String> createUserResponse = mockAuthenticatedPost("api/secure/user", apiUser, JsonUtils.toJson(otpUser));
        Assertions.assertEquals(200, createUserResponse.statusCode());
        OtpUser otpUserResponse = JsonUtils.getPOJOFromJSON(createUserResponse.body(), OtpUser.class);
        // Create a monitored trip for the user.
        // TODO use utils from trip monitoring PR.
        MonitoredTrip trip = new MonitoredTrip();
        trip.monday = true;
        HttpResponse<String> createTripResponse = mockAuthenticatedPost("api/secure/monitoredtrip", otpUserResponse, JsonUtils.toJson(trip));
        Assertions.assertEquals(200, createTripResponse.statusCode());
        MonitoredTrip tripResponse = JsonUtils.getPOJOFromJSON(createTripResponse.body(), MonitoredTrip.class);
        // TODO: Plan trip with OTP proxy.
        // Delete otp user.
        HttpResponse<String> deleteUserResponse = mockAuthenticatedRequest(
            String.format("api/secure/user/%s", otpUserResponse.id),
            HttpUtils.REQUEST_METHOD.DELETE,
            otpUserResponse
        );
        // Verify user no longer exists.
        OtpUser deletedOtpUser = Persistence.otpUsers.getById(otpUserResponse.id);
        Assertions.assertNull(deletedOtpUser);
        // Verify monitored trip no longer exists.
        MonitoredTrip deletedTrip = Persistence.monitoredTrips.getById(tripResponse.id);
        Assertions.assertNull(deletedTrip);
        // TODO: Verify that trip request history is gone.
        // Delete API user (this would happen through the OTP Admin portal).
        HttpResponse<String> deleteApiUserResponse = mockAuthenticatedRequest(
            String.format("api/secure/application/%s", apiUser.id),
            HttpUtils.REQUEST_METHOD.DELETE,
            apiUser
        );
        // Verify that API user is deleted.
        ApiUser deletedApiUser = Persistence.apiUsers.getById(apiUser.id);
        Assertions.assertNull(deletedApiUser);
    }
}
