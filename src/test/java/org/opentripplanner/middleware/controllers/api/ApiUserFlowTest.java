package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.utils.CreateApiKeyException;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.MockOtpResponseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Users.createAuth0UserForEmail;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.DEFAULT_USAGE_PLAN_ID;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_GRAPHQL_ENDPOINT;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeDeleteRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeGetRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedPlanPost;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;

/**
 * Tests to simulate API user flow. The following config parameters must be set in configurations/default/env.yml for
 * these end-to-end tests to run:
 *
 * AUTH0_DOMAIN set to a valid Auth0 domain.
 *
 * AUTH0_API_CLIENT set to a valid Auth0 application client id.
 *
 * AUTH0_API_SECRET set to a valid Auth0 application client secret.
 *
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id (AWS requires this to create an api key).
 *
 * OTP_API_ROOT set to a live OTP instance (e.g. http://otp-server.example.com/otp).
 *
 * OTP_PLAN_ENDPOINT set to a live OTP plan endpoint (e.g. /routers/default/plan).
 *
 * An AWS_PROFILE is required, or AWS access has been configured for your operating environment e.g.
 * C:\Users\<username>\.aws\credentials in Windows or Mac OS equivalent.
 *
 * The following environment variable must be set for these tests to run: - RUN_E2E=true.
 *
 * Auth0 must be correctly configured as described here: https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow
 */
public class ApiUserFlowTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserFlowTest.class);
    private static ApiUser apiUser;
    private static OtpUser otpUser;
    private static OtpUser otpUserMatchingApiUser;
    private static OtpUser otpUserStandalone;
    private static final String OTP_USER_PATH = "api/secure/user";
    private static final String MONITORED_TRIP_PATH = "api/secure/monitoredtrip";
    private static final String OTP_PROXY_ENDPOINT = "/otp";

    /**
     * Whether tests for this class should run. End to End must be enabled and Auth must NOT be disabled. This should be
     * evaluated after the middleware application starts up (to ensure default disableAuth value has been applied from
     * config).
     */
    private static boolean testsShouldRun() {
        return IS_END_TO_END && !isAuthDisabled();
    }

    /**
     * Create an {@link ApiUser} and an {@link OtpUser} prior to unit tests
     */
    @BeforeAll
    public static void setUp() throws CreateApiKeyException {
        assumeTrue(testsShouldRun());
        // Mock the OTP server TODO: Run a live OTP instance?
        OtpTestUtils.mockOtpServer();
        // As a pre-condition, create an API User with API key.
        apiUser = PersistenceTestUtils.createApiUser(String.format("test-%s@example.com", UUID.randomUUID().toString()));
        // Create a 'standalone' OtpUser that is not managed by an ApiUser.
        otpUserStandalone = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-stdalone-otpuser"));

        // As a pre-condition, create an API User with API key.
        apiUser = PersistenceTestUtils.createApiUser(ApiTestUtils.generateEmailAddress("test-apiuser"));
        apiUser.createApiKey(DEFAULT_USAGE_PLAN_ID, true);
        // Create, but do not persist, an OTP user.
        otpUser = new OtpUser();
        otpUser.email = ApiTestUtils.generateEmailAddress("test-api-otpuser");
        otpUser.hasConsentedToTerms = true;
        otpUser.storeTripHistory = true;
        try {
            // create Auth0 user for apiUser.
            User auth0User = createAuth0UserForEmail(apiUser.email, TEMP_AUTH0_USER_PASSWORD);
            // update api user with valid auth0 user ID (so the Auth0 delete works)
            apiUser.auth0UserId = auth0User.getId();
            Persistence.apiUsers.replace(apiUser.id, apiUser);
            // create Otp user matching Api user to aid with testing edge cases.
            otpUserMatchingApiUser = new OtpUser();
            otpUserMatchingApiUser.email = apiUser.email;
            otpUserMatchingApiUser.auth0UserId = apiUser.auth0UserId;
            Persistence.otpUsers.create(otpUserMatchingApiUser);
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(testsShouldRun());
        apiUser = Persistence.apiUsers.getById(apiUser.id);
        if (apiUser != null) apiUser.delete();
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete(false);
        otpUserMatchingApiUser = Persistence.otpUsers.getById(otpUserMatchingApiUser.id);
        if (otpUserMatchingApiUser != null) otpUserMatchingApiUser.delete(false);
        otpUserStandalone = Persistence.otpUsers.getById(otpUserStandalone.id);
        if (otpUserStandalone != null) otpUserStandalone.delete(false);
    }

    /**
     * Tests to confirm that an otp user, related monitored trip and plan can be created and deleted leaving no orphaned
     * records. This also includes Auth0 users if auth is enabled. The basic script for this test is as follows:
     *   1. Start with existing API User that has a valid API key.
     *   2. Use the API key to make a request to the authenticate endpoint to get Auth0 token for further requests.
     *   3. Create OTP user.
     *   4. Make subsequent requests on behalf of OTP users.
     *   5. Delete user and verify that their associated objects are also deleted.
     */
    @Test
    public void canSimulateApiUserFlow() throws Exception {

        // Define the header values to be used in requests from this point forward.
        HashMap<String, String> apiUserHeaders = new HashMap<>();
        apiUserHeaders.put("x-api-key", apiUser.apiKeys.get(0).value);
        // obtain Auth0 token for Api user.
        String authenticateEndpoint = String.format("api/secure/application/authenticate?username=%s&password=%s",
            apiUser.email,
            TEMP_AUTH0_USER_PASSWORD);
        HttpResponseValues getTokenResponse = makeRequest(authenticateEndpoint,
            "",
            apiUserHeaders,
            HttpMethod.POST
        );
        // Note: do not log the Auth0 token (could be a security risk).
        LOG.info("Token response status: {}", getTokenResponse.status);
        assertEquals(HttpStatus.OK_200, getTokenResponse.status);
        TokenHolder tokenHolder = JsonUtils.getPOJOFromJSON(getTokenResponse.responseBody, TokenHolder.class);

        // Define the bearer value to be used in requests from this point forward.
        apiUserHeaders.put("Authorization", "Bearer " + tokenHolder.getAccessToken());

        // create an Otp user authenticating as an Api user.
        HttpResponseValues createUserResponse = makeRequest(OTP_USER_PATH,
            JsonUtils.toJson(otpUser),
            apiUserHeaders,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, createUserResponse.status);

        // Request all Otp users created by an Api user. This will work and return all Otp users.
        HttpResponseValues getAllOtpUsersCreatedByApiUser = makeGetRequest(OTP_USER_PATH, apiUserHeaders);
        assertEquals(HttpStatus.OK_200, getAllOtpUsersCreatedByApiUser.status);
        ResponseList<OtpUser> otpUsers =
            JsonUtils.getResponseListFromJSON(getAllOtpUsersCreatedByApiUser.responseBody, OtpUser.class);
        assertEquals(1, otpUsers.total);

        // Attempt to create a monitored trip for an Otp user using mock authentication. This will fail because the user
        // was created by an Api user and therefore does not have a Auth0 account.
        OtpUser otpUserResponse = JsonUtils.getPOJOFromJSON(createUserResponse.responseBody, OtpUser.class);

        // Create a monitored trip for the Otp user (API users are prevented from doing this).
        // TODO: refactor this call
        MonitoredTrip monitoredTrip = new MonitoredTrip(OtpTestUtils.getSampleQueryParams(), OtpTestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = otpUser.id;
        HttpResponseValues createTripResponseAsOtpUser = mockAuthenticatedRequest(
            MONITORED_TRIP_PATH,
            JsonUtils.toJson(monitoredTrip),
            otpUserResponse,
            HttpMethod.POST
        );
        assertEquals(HttpStatus.UNAUTHORIZED_401, createTripResponseAsOtpUser.status);

        // Create a monitored trip for an Otp user authenticating as an Api user. An Api user can create a monitored
        // trip for an Otp user they created.

        // Set mock OTP responses so that trip existence checks in the
        // POST call below to save the monitored trip can pass.
        MockOtpResponseProvider mockResponses = new MockOtpResponseProvider(OtpTestUtils.createMockOtpResponsesForTripExistence());
        ItineraryExistence.otpResponseProviderOverride = mockResponses::getMockResponse;

        HttpResponseValues createTripResponseAsApiUser = makeRequest(
            MONITORED_TRIP_PATH,
            JsonUtils.toJson(monitoredTrip),
            apiUserHeaders,
            HttpMethod.POST
        );

        // After POST is complete, reset OTP response provider to default.
        ItineraryExistence.otpResponseProviderOverride = null;
        // Make sure all mocks were used
        assertTrue(mockResponses.areAllMocksUsed());

        String responseBody = createTripResponseAsApiUser.responseBody;
        assertEquals(HttpStatus.OK_200, createTripResponseAsApiUser.status);
        MonitoredTrip monitoredTripResponse = JsonUtils.getPOJOFromJSON(responseBody, MonitoredTrip.class);

        // As API user, try to assign this trip to another user the API user doesn't manage.
        // (This trip should not be persisted.)
        MonitoredTrip monitoredTripToNonManagedUser = JsonUtils.getPOJOFromJSON(responseBody, MonitoredTrip.class);
        monitoredTripToNonManagedUser.userId = otpUserStandalone.id;
        HttpResponseValues putTripResponseAsApiUser = makeRequest(
            MONITORED_TRIP_PATH + "/" + monitoredTripToNonManagedUser.id,
            JsonUtils.toJson(monitoredTripToNonManagedUser),
            apiUserHeaders,
            HttpMethod.PUT
        );
        assertEquals(HttpStatus.FORBIDDEN_403, putTripResponseAsApiUser.status);

        // Request all monitored trips for an Otp user authenticating as an Api user. This will work and return all trips
        // matching the user id provided.
        HttpResponseValues getAllMonitoredTripsForOtpUser = makeGetRequest(
            String.format("api/secure/monitoredtrip?userId=%s", otpUserResponse.id),
            apiUserHeaders
        );
        assertEquals(HttpStatus.OK_200, getAllMonitoredTripsForOtpUser.status);

        // Request all monitored trips for an Otp user authenticating as an Api user, without defining the user id. This
        // will fail because an Api user must provide a user id.
        getAllMonitoredTripsForOtpUser = makeRequest(MONITORED_TRIP_PATH,
            "",
            apiUserHeaders,
            HttpMethod.GET
        );
        assertEquals(HttpStatus.BAD_REQUEST_400, getAllMonitoredTripsForOtpUser.status);


        // Plan trip with OTP proxy authenticating as an OTP user. Mock plan response will be returned. This will work
        // as an Otp user (created by MOD UI or an Api user) because the end point has no auth. A lack of auth also means
        // the plan is not saved.
        String otpQueryForOtpUserRequest = OTP_PROXY_ENDPOINT + OTP_GRAPHQL_ENDPOINT;
        HttpResponseValues planTripResponseAsOtpUser = mockAuthenticatedPlanPost(
            otpQueryForOtpUserRequest,
            monitoredTrip.otp2QueryParams,
            ApiTestUtils.getMockHeaders(otpUserResponse),
            otpUserResponse
        );
        LOG.info("OTP user: Plan trip response: {}\n....",
            planTripResponseAsOtpUser.responseBody.substring(0, 300));
        assertEquals(HttpStatus.OK_200, planTripResponseAsOtpUser.status);

        // Plan trip with OTP proxy authenticating as an Api user. Mock plan response will be returned. This will work
        // as an Api user because the end point has no auth.
        String otpQueryForApiUserRequest = OTP_PROXY_ENDPOINT + OTP_GRAPHQL_ENDPOINT +
            String.format("?userId=%s",otpUserResponse.id);
        HttpResponseValues planTripResponseAsApiUser = mockAuthenticatedPlanPost(
            otpQueryForApiUserRequest,
            monitoredTrip.otp2QueryParams,
            apiUserHeaders,
            otpUserResponse
        );
        LOG.info("API user (on behalf of an Otp user): Plan trip response: {}\n....",
            planTripResponseAsApiUser.responseBody.substring(0, 300));
        assertEquals(HttpStatus.OK_200, planTripResponseAsApiUser.status);

        // Get trip request history for user authenticating as an Otp user. This will fail because the user was created
        // by an Api user and therefore does not have a Auth0 account.
        String tripRequestsPath = String.format("api/secure/triprequests?userId=%s", otpUserResponse.id);
        HttpResponseValues tripRequestResponseAsOtUser = mockAuthenticatedGet(tripRequestsPath, otpUserResponse);

        assertEquals(HttpStatus.UNAUTHORIZED_401, tripRequestResponseAsOtUser.status);

        // Get trip request history for user authenticating as an Api user. This will work because an Api user is able
        // to get a trip on behalf of an Otp user they created.
        HttpResponseValues tripRequestResponseAsApiUser = makeGetRequest(tripRequestsPath, apiUserHeaders);
        assertEquals(HttpStatus.OK_200, tripRequestResponseAsApiUser.status);

        ResponseList<TripRequest> tripRequests =
            JsonUtils.getResponseListFromJSON(tripRequestResponseAsApiUser.responseBody,
                TripRequest.class);

        // Delete Otp user authenticating as an Otp user. This will fail because the user was created by an Api user and
        // therefore does not have a Auth0 account.
        String otpUserPath = String.format("api/secure/user/%s", otpUserResponse.id);
        HttpResponseValues deleteUserResponseAsOtpUser = mockAuthenticatedGet(otpUserPath, otpUserResponse);
        assertEquals(HttpStatus.UNAUTHORIZED_401, deleteUserResponseAsOtpUser.status);

        // Delete Otp user authenticating as an Api user. This will work because an Api user can delete an Otp user they
        // created.
        HttpResponseValues deleteUserResponseAsApiUser = makeDeleteRequest(otpUserPath, apiUserHeaders);
        assertEquals(HttpStatus.OK_200, deleteUserResponseAsApiUser.status);

        // Verify user no longer exists.
        OtpUser deletedOtpUser = Persistence.otpUsers.getById(otpUserResponse.id);
        assertNull(deletedOtpUser);

        // Verify monitored trip no longer exists.
        MonitoredTrip deletedTrip = Persistence.monitoredTrips.getById(monitoredTripResponse.id);
        assertNull(deletedTrip);

        // Verify trip request no longer exists.
        TripRequest tripRequestFromResponse = tripRequests.data.get(0);
        TripRequest tripRequestFromDb = Persistence.tripRequests.getById(tripRequestFromResponse.id);
        assertNull(tripRequestFromDb);

        // Delete API user (this would happen through the OTP Admin portal).
        HttpResponseValues deleteApiUserResponse = makeDeleteRequest(
            String.format("api/secure/application/%s", apiUser.id),
            apiUserHeaders
        );
        assertEquals(HttpStatus.OK_200, deleteApiUserResponse.status);

        // Verify that API user is deleted.
        ApiUser deletedApiUser = Persistence.apiUsers.getById(apiUser.id);
        assertNull(deletedApiUser);
    }
}
