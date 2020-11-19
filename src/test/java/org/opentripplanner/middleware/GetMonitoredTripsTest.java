package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AdminUser;
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
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.TestUtils.isEndToEnd;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;

/**
 * Tests to simulate getting trips as an Otp user with enhanced admin credentials. The following config parameters must
 * be set in configurations/default/env.yml for these end-to-end tests to run:
 *
 * AUTH0_DOMAIN set to a valid Auth0 domain.
 *
 * AUTH0_API_CLIENT set to a valid Auth0 application client id.
 *
 * AUTH0_API_SECRET set to a valid Auth0 application client secret.
 *
 * OTP_API_ROOT set to a live OTP instance (e.g. http://otp-server.example.com/otp).
 *
 * OTP_PLAN_ENDPOINT set to a live OTP plan endpoint (e.g. /routers/default/plan).
 *
 * The following environment variable must be set for these tests to run: - RUN_E2E=true.
 *
 * Auth0 must be correctly configured as described here: https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow
 */
public class GetMonitoredTripsTest {
    private static AdminUser multiAdminUser;
    private static OtpUser soloOtpUser;
    private static OtpUser multiOtpUser;
    private static final String MONITORED_TRIP_PATH = "api/secure/monitoredtrip";
    private static final Logger LOG = LoggerFactory.getLogger(GetMonitoredTripsTest.class);

    /**
     * Create Otp and Admin user accounts. Create Auth0 account for just the Otp users. If
     * an Auth0 account is created for the admin user it will fail because the email address already exists.
     */
    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        // Load config before checking if tests should run.
        OtpMiddlewareTest.setUp();
        assumeTrue(isEndToEnd);
        setAuthDisabled(false);
        // Mock the OTP server TODO: Run a live OTP instance?
        TestUtils.mockOtpServer();
        String multiUserEmail = String.format("test-%s@example.com", UUID.randomUUID().toString());
        soloOtpUser = PersistenceUtil.createUser(String.format("test-%s@example.com", UUID.randomUUID().toString()));
        multiOtpUser = PersistenceUtil.createUser(multiUserEmail);
        multiAdminUser = PersistenceUtil.createAdminUser(multiUserEmail);
        try {
            // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
            // from being able to use TEMP_AUTH0_USER_PASSWORD.
            User auth0User = Auth0Users.createAuth0UserForEmail(soloOtpUser.email, TEMP_AUTH0_USER_PASSWORD);
            soloOtpUser.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(soloOtpUser.id, soloOtpUser);
            auth0User = Auth0Users.createAuth0UserForEmail(multiUserEmail, TEMP_AUTH0_USER_PASSWORD);
            multiOtpUser.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(multiOtpUser.id, multiOtpUser);
            // Use the same Auth0 user id as otpUser2 as the email address is the same.
            multiAdminUser.auth0UserId = auth0User.getId();
            Persistence.adminUsers.replace(multiAdminUser.id, multiAdminUser);
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(isEndToEnd);
        restoreDefaultAuthDisabled();
        soloOtpUser = Persistence.otpUsers.getById(soloOtpUser.id);
        if (soloOtpUser != null) soloOtpUser.delete(false);
        multiOtpUser = Persistence.otpUsers.getById(multiOtpUser.id);
        if (multiOtpUser != null) multiOtpUser.delete(false);
        multiAdminUser = Persistence.adminUsers.getById(multiAdminUser.id);
        if (multiAdminUser != null) multiAdminUser.delete();
    }

    /**
     * Create trips for two different Otp users and attempt to get both trips with Otp user that has 'enhanced' admin
     * credentials.
     */
    @Test
    public void canGetOwnMonitoredTrips() throws URISyntaxException, JsonProcessingException {

        // Create trip as Otp user 1.
        MonitoredTrip monitoredTrip = new MonitoredTrip(TestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = soloOtpUser.id;
        HttpResponse<String> createTrip1Response = mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(monitoredTrip),
            soloOtpUser,
            HttpMethod.POST
        );
        assertEquals(HttpStatus.OK_200, createTrip1Response.statusCode());

        // Create trip as Otp user 2.
        monitoredTrip = new MonitoredTrip(TestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = multiOtpUser.id;
        HttpResponse<String> createTripResponse2 = mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(monitoredTrip),
            multiOtpUser,
            HttpMethod.POST
        );
        assertEquals(HttpStatus.OK_200, createTripResponse2.statusCode());

        // Get trips for solo Otp user.
        HttpResponse<String> soloTripsResponse = mockAuthenticatedGet(MONITORED_TRIP_PATH, soloOtpUser);
        ResponseList<MonitoredTrip> soloTrips = JsonUtils.getResponseListFromJSON(soloTripsResponse.body(), MonitoredTrip.class);

        // Expect only 1 trip for solo Otp user.
        assertEquals(1, soloTrips.data.size());

        // Get trips for multi Otp user/admin user.
        HttpResponse<String> multiTripsResponse = mockAuthenticatedGet(MONITORED_TRIP_PATH, multiOtpUser);
        ResponseList<MonitoredTrip> multiTrips = JsonUtils.getResponseListFromJSON(multiTripsResponse.body(), MonitoredTrip.class);

        // Multi Otp user has 'enhanced' admin credentials both trips will be returned. The expectation here is that the UI
        // will always provide the user id to prevent this (as with the next test).
        // TODO: Determine if a separate admin endpoint should be maintained for getting all/combined trips.
        assertEquals(2, multiTrips.data.size());

        // Get trips for only the multi Otp user by specifying Otp user id.
        HttpResponse<String> tripsFilteredResponse = mockAuthenticatedGet(
            String.format("api/secure/monitoredtrip?userId=%s", multiOtpUser.id),
            multiOtpUser
        );
        ResponseList<MonitoredTrip> tripsFiltered = JsonUtils.getResponseListFromJSON(tripsFilteredResponse.body(), MonitoredTrip.class);
        // Just the trip for Otp user 2 will be returned.
        assertEquals(1, tripsFiltered.data.size());
    }
}
