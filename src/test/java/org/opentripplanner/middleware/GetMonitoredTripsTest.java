package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.PersistenceUtil;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.TestUtils.isEndToEnd;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedPost;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;

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
    private static AdminUser adminUser;
    private static OtpUser otpUser1;
    private static OtpUser otpUser2;

    /**
     * Whether tests for this class should run. End to End must be enabled and Auth must NOT be disabled. This should be
     * evaluated after the middleware application starts up (to ensure default disableAuth value has been applied from
     * config).
     */
    private static boolean testsShouldRun() {
        return isEndToEnd && !isAuthDisabled();
    }

    /**
     * Create Otp and Admin user accounts. Create Auth0 account for just the Otp users. If
     * an Auth0 account is created for the admin user it will fail because the email address already exists.
     */
    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        // Load config before checking if tests should run.
        OtpMiddlewareTest.setUp();
        assumeTrue(testsShouldRun());
        // Mock the OTP server TODO: Run a live OTP instance?
        TestUtils.mockOtpServer();
        String email = String.format("test-%s@example.com", UUID.randomUUID().toString());
        otpUser1 = PersistenceUtil.createUser(String.format("test-%s@example.com", UUID.randomUUID().toString()));
        otpUser2 = PersistenceUtil.createUser(email);
        adminUser = PersistenceUtil.createAdminUser(email);
        try {
            User auth0User = Auth0Users.createAuth0UserForEmail(otpUser1.email, TEMP_AUTH0_USER_PASSWORD);
            otpUser1.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(otpUser1.id, otpUser1);
            auth0User = Auth0Users.createAuth0UserForEmail(otpUser2.email, TEMP_AUTH0_USER_PASSWORD);
            otpUser2.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(otpUser2.id, otpUser2);
            // Uncommenting will fail set-up because the email address already exists with Auth0.
//            auth0User = createAuth0UserForEmail(otpUser.email, TEMP_AUTH0_USER_PASSWORD);
            adminUser.auth0UserId = auth0User.getId();
            Persistence.adminUsers.replace(adminUser.id, adminUser);
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
        otpUser1 = Persistence.otpUsers.getById(otpUser1.id);
        if (otpUser1 != null) otpUser1.delete(false);
        otpUser2 = Persistence.otpUsers.getById(otpUser2.id);
        if (otpUser2 != null) otpUser2.delete(false);
        adminUser = Persistence.adminUsers.getById(adminUser.id);
        if (adminUser != null) adminUser.delete();
    }

    /**
     * Create trips for two different Otp users and attempt to get both trips with Otp user that has 'enhanced' admin
     * credentials.
     */
    @Test
    public void canGetOwnMonitoredTrips() {

        // Create trip as Otp user 1.
        MonitoredTrip monitoredTrip = new MonitoredTrip(TestUtils.sendSamplePlanRequest());
        monitoredTrip.userId = otpUser1.id;
        HttpResponse<String> response = mockAuthenticatedPost("api/secure/monitoredtrip",
            otpUser1,
            JsonUtils.toJson(monitoredTrip)
        );
        assertEquals(HttpStatus.OK_200, response.statusCode());

        // Create trip as Otp user 2.
        monitoredTrip = new MonitoredTrip(TestUtils.sendSamplePlanRequest());
        monitoredTrip.userId = otpUser2.id;
        response = mockAuthenticatedPost("api/secure/monitoredtrip",
            otpUser2,
            JsonUtils.toJson(monitoredTrip)
        );
        assertEquals(HttpStatus.OK_200, response.statusCode());

        // Get trips for Otp user 2.
        response = mockAuthenticatedRequest("api/secure/monitoredtrip",
            otpUser2,
            HttpUtils.REQUEST_METHOD.GET
        );
        ResponseList tripRequests = JsonUtils.getPOJOFromJSON(response.body(), ResponseList.class);

        // Although Otp user 2 has 'enhanced' admin credentials a single trip will be returned.
        assertEquals(1, tripRequests.data.size());

        // Get trips for Otp user 2 defining user id.
        response = mockAuthenticatedRequest(String.format("api/secure/monitoredtrip?userId=%s", otpUser2.id),
            otpUser2,
            HttpUtils.REQUEST_METHOD.GET
        );
        tripRequests = JsonUtils.getPOJOFromJSON(response.body(), ResponseList.class);

        // Just the trip for Otp user 2 will be returned.
        assertEquals(1, tripRequests.data.size());
    }
}
