package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.users.User;
import com.mongodb.BasicDBObject;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.Date;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.createAndAssignAuth0User;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.deleteOtpUser;

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
public class MonitoredTripControllerTest extends OtpMiddlewareTestEnvironment {
    private static AdminUser multiAdminUser;
    private static OtpUser soloOtpUser;
    private static OtpUser multiOtpUser;
    private static final String MONITORED_TRIP_PATH = "api/secure/monitoredtrip";

    private static final String UI_QUERY_PARAMS
        = "?fromPlace=fromplace%3A%3A28.556631%2C-81.411781&toPlace=toplace%3A%3A28.545925%2C-81.348609&date=2020-11-13&time=14%3A21&arriveBy=false&mode=WALK%2CBUS%2CRAIL&numItineraries=3";
    private static final String DUMMY_STRING = "ABCDxyz";
    private static HashMap<String, String> guardianHeaders;

    /**
     * Create Otp and Admin user accounts. Create Auth0 account for just the Otp users. If
     * an Auth0 account is created for the admin user it will fail because the email address already exists.
     */
    @BeforeAll
    public static void setUp() {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);

        String multiUserEmail = ApiTestUtils.generateEmailAddress("test-multiotpuser");
        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
        multiOtpUser = PersistenceTestUtils.createUser(multiUserEmail);
        multiAdminUser = PersistenceTestUtils.createAdminUser(multiUserEmail);
        try {
            createAndAssignAuth0User(soloOtpUser);
            User auth0User = Auth0Users.createAuth0UserForEmail(multiUserEmail, TEMP_AUTH0_USER_PASSWORD);
            multiOtpUser.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(multiOtpUser.id, multiOtpUser);
            // Use the same Auth0 user id as multiOtpUser as the email address is the same.
            multiAdminUser.auth0UserId = auth0User.getId();
            Persistence.adminUsers.replace(multiAdminUser.id, multiAdminUser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(IS_END_TO_END);
        restoreDefaultAuthDisabled();
        multiAdminUser = Persistence.adminUsers.getById(multiAdminUser.id);
        if (multiAdminUser != null) multiAdminUser.delete();
        deleteOtpUser(
            IS_END_TO_END,
            soloOtpUser,
            multiOtpUser
        );
    }

    @AfterEach
    public void tearDownAfterTest() {
        Persistence.monitoredTrips.removeFiltered(getUserFilter(soloOtpUser.id));
        Persistence.monitoredTrips.removeFiltered(getUserFilter(multiOtpUser.id));
    }

    private static BasicDBObject getUserFilter(String id) {
        BasicDBObject userFilter = new BasicDBObject();
        userFilter.put("userId", id);
        return userFilter;
    }

    /**
     * Create trips for two different Otp users and attempt to get both trips with Otp user that has 'enhanced' admin
     * credentials.
     */
    @Test
    void canGetOwnMonitoredTrips() throws Exception {
        // Create a trip for the solo and the multi OTP user.
        createMonitoredTripForUser(soloOtpUser);
        createMonitoredTripForUser(multiOtpUser);

        // Get trips for solo Otp user.
        ResponseList<MonitoredTrip> soloTrips = getMonitoredTripsForUser(MONITORED_TRIP_PATH, soloOtpUser);
        // Expect only 1 trip for solo Otp user.
        assertEquals(1, soloTrips.data.size());

        // Certain fields such as tripTime should be null when obtained from parsing JSON responses
        // because of the @JsonIgnore annotation.
        assertNull(soloTrips.data.get(0).tripTime);

        // Get trips for multi Otp user/admin user.
        ResponseList<MonitoredTrip> multiTrips = getMonitoredTripsForUser(MONITORED_TRIP_PATH, multiOtpUser);

        // Multi Otp user has 'enhanced' admin credentials, still expect only 1 trip to be returned as the scope will
        // limit the requesting user to a single 'otp-user' user type.
        // TODO: Determine if a separate admin endpoint should be maintained for getting all/combined trips.
        assertEquals(1, multiTrips.data.size());

        // Get trips for only the multi Otp user by specifying Otp user id.
        ResponseList<MonitoredTrip> tripsFiltered = getMonitoredTripsForUser(
            String.format("%s?userId=%s", MONITORED_TRIP_PATH, multiOtpUser.id), multiOtpUser
        );
        // Just the trip for Otp user 2 will be returned.
        assertEquals(1, tripsFiltered.data.size());
    }

    @Test
    void canPreserveTripFields() throws Exception {
        // Create a trip for the solo OTP user.
        createMonitoredTripForUser(soloOtpUser);

        BasicDBObject filter = getUserFilter(soloOtpUser.id);

        // Expect only 1 trip for solo Otp user.
        assertEquals(1, Persistence.monitoredTrips.getCountFiltered(filter));

        MonitoredTrip originalTrip = Persistence.monitoredTrips.getOneFiltered(filter);
        assertNotNull(originalTrip.itinerary);
        assertNotNull(originalTrip.itineraryExistence);
        // Can't really assert journeyState because itinerary checks will not be run for these tests.
        assertNotEquals(DUMMY_STRING, originalTrip.tripTime);
        assertNotEquals(DUMMY_STRING, originalTrip.queryParams);
        assertNotEquals(DUMMY_STRING, originalTrip.userId);
        assertNotNull(originalTrip.from);
        assertNotNull(originalTrip.to);

        MonitoredTrip modifiedTrip = new MonitoredTrip();
        modifiedTrip.id = originalTrip.id;
        modifiedTrip.tripTime = DUMMY_STRING;
        modifiedTrip.queryParams = DUMMY_STRING;
        modifiedTrip.userId = DUMMY_STRING;

        mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(modifiedTrip),
            soloOtpUser,
            HttpMethod.PUT
        );

        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(originalTrip.id);
        assertEquals(updatedTrip.itinerary.startTime, originalTrip.itinerary.startTime);
        assertEquals(updatedTrip.tripTime, originalTrip.tripTime);
        assertEquals(updatedTrip.queryParams, originalTrip.queryParams);
        assertEquals(updatedTrip.userId, originalTrip.userId);
        assertEquals(updatedTrip.from.name, originalTrip.from.name);
        assertEquals(updatedTrip.to.name, originalTrip.to.name);
    }

    /**
     * Helper method to get trips for user.
     */
    private ResponseList<MonitoredTrip> getMonitoredTripsForUser(String path, OtpUser otpUser) throws Exception {
        HttpResponseValues soloTripsResponse = mockAuthenticatedGet(path, otpUser);
        return JsonUtils.getResponseListFromJSON(soloTripsResponse.responseBody, MonitoredTrip.class);
    }

    /**
     * Creates a {@link MonitoredTrip} for the specified user.
     */
    private static void createMonitoredTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = otpUser.id;
        monitoredTrip.tripTime = "08:35";
        monitoredTrip.queryParams = UI_QUERY_PARAMS;
        monitoredTrip.itinerary = new Itinerary();
        monitoredTrip.itinerary.startTime = new Date();
        monitoredTrip.itineraryExistence = new ItineraryExistence();
        monitoredTrip.from = new Place();
        monitoredTrip.from.name = "From Place";
        monitoredTrip.to = new Place();
        monitoredTrip.to.name = "To Place";

        Persistence.monitoredTrips.create(monitoredTrip);
    }
}
