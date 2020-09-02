package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedPost;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.auth.Auth0Connection.authDisabled;
import static org.opentripplanner.middleware.auth.Auth0Users.createAuth0UserForEmail;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.DEFAULT_USAGE_PLAN_ID;
import static org.opentripplanner.middleware.otp.OtpRequestProcessor.OTP_PLAN_ENDPOINT;
import static org.opentripplanner.middleware.otp.OtpRequestProcessor.OTP_PROXY_ENDPOINT;

/**
 * Tests to simulate API user flow. The following config parameters must be set in configurations/default/env.yml for
 * these end-to-end tests to run: - AUTH0_DOMAIN set to a valid Auth0 domain - AUTH0_API_CLIENT set to a valid Auth0
 * application client id - AUTH0_API_SECRET set to a valid Auth0 application client secret - DEFAULT_USAGE_PLAN_ID set
 * to a valid usage plan id (AWS requires this to create an api key) - OTP_API_ROOT set to http://localhost:8080/otp so
 * the mock OTP server is used - OTP_PLAN_ENDPOINT set to /plan - An AWS_PROFILE is required, or AWS access has been
 * configured for your operating environment e.g. C:\Users\<username>\.aws\credentials in Windows or Mac OS equivalent.
 *
 * The following environment variable must be set for these tests to run: - RUN_E2E=true
 *
 * Auth0 must be correctly configured as described here: https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow
 */
public class ApiUserFlowTest {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserFlowTest.class);
    private static ApiUser apiUser;
    private static OtpUser otpUser;
    private static boolean testsShouldRun = getBooleanEnvVar("RUN_E2E") && !authDisabled();

    /**
     * Create an {@link ApiUser} and an {@link AdminUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws IOException {
        assumeTrue(testsShouldRun);
        OtpMiddlewareTest.setUp();
        // Mock the OTP server TODO: Run a live OTP instance?
        TestUtils.mockOtpServer();
        // As a pre-condition, create an API User with API key.
        apiUser = PersistenceUtil.createApiUser(String.format("test-%s@example.com", UUID.randomUUID().toString()));
        apiUser.createApiKey(DEFAULT_USAGE_PLAN_ID, true);
        // Create, but do not persist, an OTP user. Also
        otpUser = new OtpUser();
        otpUser.email = String.format("test-%s@example.com", UUID.randomUUID().toString());
        otpUser.hasConsentedToTerms = true;
        otpUser.storeTripHistory = true;
        // create Auth0 users for apiUser and optUser.
        try {
            User auth0User = createAuth0UserForEmail(apiUser.email, TestUtils.TEMP_AUTH0_USER_PASSWORD);
            // update api user with valid auth0 user ID (so the Auth0 delete works)
            apiUser.auth0UserId = auth0User.getId();
            Persistence.apiUsers.replace(apiUser.id, apiUser);

            auth0User = createAuth0UserForEmail(otpUser.email, TestUtils.TEMP_AUTH0_USER_PASSWORD);
            // update otp user with valid auth0 user ID (so the Auth0 delete works)
            otpUser.auth0UserId = auth0User.getId();
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(testsShouldRun);
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        if (apiUser != null) apiUser.delete();
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete();
    }

    /**
     * Tests to confirm that an otp user, related monitored trip and plan can be created and deleted leaving no orphaned
     * records. This also includes Auth0 users if auth is enabled.
     */
    @Test
    public void canSimulateApiUserFlow() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));

        // create otp user as api user
        HttpResponse<String> createUserResponse = mockAuthenticatedPost("api/secure/user",
            apiUser,
            JsonUtils.toJson(otpUser)
        );
        assertEquals(HttpStatus.OK_200, createUserResponse.statusCode());
        OtpUser otpUserResponse = JsonUtils.getPOJOFromJSON(createUserResponse.body(), OtpUser.class);

        // Create a monitored trip for the Otp user (API users are prevented from doing this).
        // TODO use utils from trip monitoring PR.
        MonitoredTrip trip = new MonitoredTrip();
        trip.monday = true;
        HttpResponse<String> createTripResponse = mockAuthenticatedPost("api/secure/monitoredtrip",
            otpUserResponse,
            JsonUtils.toJson(trip)
        );
        assertEquals(HttpStatus.OK_200, createTripResponse.statusCode());
        MonitoredTrip monitoredTripResponse = JsonUtils.getPOJOFromJSON(createTripResponse.body(), MonitoredTrip.class);

        // Plan trip with OTP proxy. Mock plan response will be returned
        String otpQuery = OTP_PROXY_ENDPOINT + OTP_PLAN_ENDPOINT + "?fromPlace=34,-87&toPlace=33.-87";
        HttpResponse<String> planTripResponse = mockAuthenticatedRequest(otpQuery,
            otpUserResponse,
            HttpUtils.REQUEST_METHOD.GET
        );
        LOG.info("Plan trip response: {}\n....", planTripResponse.body().substring(0, 300));
        assertEquals(HttpStatus.OK_200, planTripResponse.statusCode());

        // Get trip for user, before it is deleted
        HttpResponse<String> tripRequestResponse = mockAuthenticatedRequest(String.format("api/secure/triprequests?userId=%s",
            otpUserResponse.id),
            otpUserResponse,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(HttpStatus.OK_200, tripRequestResponse.statusCode());
        List<TripRequest> tripRequests = JsonUtils.getPOJOFromJSONAsList(tripRequestResponse.body(), TripRequest.class);

        // Delete otp user.
        HttpResponse<String> deleteUserResponse = mockAuthenticatedRequest(
            String.format("api/secure/user/%s", otpUserResponse.id),
            otpUserResponse,
            HttpUtils.REQUEST_METHOD.DELETE
        );
        assertEquals(HttpStatus.OK_200, deleteUserResponse.statusCode());

        // Verify user no longer exists.
        OtpUser deletedOtpUser = Persistence.otpUsers.getById(otpUserResponse.id);
        assertNull(deletedOtpUser);

        // Verify monitored trip no longer exists.
        MonitoredTrip deletedTrip = Persistence.monitoredTrips.getById(monitoredTripResponse.id);
        assertNull(deletedTrip);

        // Verify trip request no longer exists.
        TripRequest tripRequest = Persistence.tripRequests.getById(tripRequests.get(0).id);
        assertNull(tripRequest);

        // Delete API user (this would happen through the OTP Admin portal).
        HttpResponse<String> deleteApiUserResponse = mockAuthenticatedRequest(
            String.format("api/secure/application/%s", apiUser.id),
            apiUser,
            HttpUtils.REQUEST_METHOD.DELETE
        );
        assertEquals(HttpStatus.OK_200, deleteApiUserResponse.statusCode());

        // Verify that API user is deleted.
        ApiUser deletedApiUser = Persistence.apiUsers.getById(apiUser.id);
        assertNull(deletedApiUser);
    }
}
