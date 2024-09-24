package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.users.User;
import com.mongodb.BasicDBObject;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.GuardianUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeGetRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;

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
public class GetMonitoredTripsTest extends OtpMiddlewareTestEnvironment {
    private static AdminUser multiAdminUser;
    private static OtpUser soloOtpUser;
    private static OtpUser multiOtpUser;
    private static OtpUser guardianUserOne;
    private static OtpUser dependentUserOne;
    private static OtpUser guardianUserTwo;
    private static OtpUser dependentUserTwo;
    private static OtpUser guardianUserThree;
    private static OtpUser dependentUserThree;
    private static final String MONITORED_TRIP_PATH = "api/secure/monitoredtrip";
    private static final String ACCEPT_DEPENDENT_PATH = MONITORED_TRIP_PATH + "/acceptdependent";
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

        OtpTestUtils.mockOtpServer();
        String multiUserEmail = ApiTestUtils.generateEmailAddress("test-multiotpuser");
        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
        multiOtpUser = PersistenceTestUtils.createUser(multiUserEmail);
        multiAdminUser = PersistenceTestUtils.createAdminUser(multiUserEmail);
        guardianUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("guardian-one"));
        dependentUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-one"));
        guardianUserTwo = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("guardian-two"));
        dependentUserTwo = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-two"));
        guardianUserThree = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("guardian-three"));
        dependentUserThree = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-three"));
        try {
            createAndAssignAuth0User(soloOtpUser);
            guardianHeaders = createAndAssignAuth0User(guardianUserOne);
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
     * Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
     * from being able to use TEMP_AUTH0_USER_PASSWORD.
     */
    private static HashMap<String, String> createAndAssignAuth0User(OtpUser user) throws Exception {
        User auth0User = Auth0Users.createAuth0UserForEmail(user.email, TEMP_AUTH0_USER_PASSWORD);
        user.auth0UserId = auth0User.getId();
        Persistence.otpUsers.replace(user.id, user);
        return getMockHeaders(user);
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
            soloOtpUser.id,
            multiOtpUser.id,
            dependentUserOne.id,
            guardianUserOne.id,
            dependentUserTwo.id,
            guardianUserTwo.id,
            dependentUserThree.id,
            guardianUserThree.id
        );
    }

    private static void deleteOtpUser(String... ids) {
        for (String id : ids) {
            OtpUser user = Persistence.otpUsers.getById(id);
            if (user != null) user.delete(false);
        }
    }

    @AfterEach
    public void tearDownAfterTest() {
        OtpTestUtils.resetOtpMocks();
        BasicDBObject soloFilter = new BasicDBObject();
        soloFilter.put("userId", soloOtpUser.id);
        Persistence.monitoredTrips.removeFiltered(soloFilter);
        BasicDBObject multiFilter = new BasicDBObject();
        multiFilter.put("userId", multiOtpUser.id);
        Persistence.monitoredTrips.removeFiltered(multiFilter);
    }

    /**
     * Create trips for two different Otp users and attempt to get both trips with Otp user that has 'enhanced' admin
     * credentials.
     */
    @Test
    void canGetOwnMonitoredTrips() throws Exception {
        // Create a trip for the solo and the multi OTP user.
        createMonitoredTripAsUser(soloOtpUser);
        createMonitoredTripAsUser(multiOtpUser);

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
        createMonitoredTripAsUser(soloOtpUser);

        BasicDBObject filter = new BasicDBObject();
        filter.put("userId", soloOtpUser.id);

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

    @Test
    void canAcceptDependentRequest() {
        dependentUserOne.guardians.add(new GuardianUser(guardianUserOne.id, guardianUserOne.email, GuardianUser.GuardianUserStatus.PENDING));
        Persistence.otpUsers.replace(dependentUserOne.id, dependentUserOne);

        String path = String.format("%s?userId=%s", ACCEPT_DEPENDENT_PATH, dependentUserOne.id);
        makeGetRequest(path, guardianHeaders);

        guardianUserOne = Persistence.otpUsers.getById(guardianUserOne.id);
        assertTrue(guardianUserOne.dependents.contains(dependentUserOne.id));

        dependentUserOne = Persistence.otpUsers.getById(dependentUserOne.id);
        List<GuardianUser> guardians = dependentUserOne.guardians;
        guardians
            .stream()
            .filter(user -> user.userId.equals(guardianUserOne.id))
            .forEach(user -> assertEquals(GuardianUser.GuardianUserStatus.CONFIRMED, user.status));
    }

    @Test
    void canInvalidateDependent() {
        guardianUserTwo.dependents.add(dependentUserTwo.id);
        Persistence.otpUsers.replace(guardianUserTwo.id, guardianUserTwo);
        dependentUserTwo.guardians.add(new GuardianUser(guardianUserTwo.id, guardianUserTwo.email, GuardianUser.GuardianUserStatus.CONFIRMED));
        Persistence.otpUsers.replace(dependentUserTwo.id, dependentUserTwo);
        guardianUserTwo.delete(false);
        dependentUserTwo = Persistence.otpUsers.getById(dependentUserTwo.id);
        GuardianUser guardianUser = dependentUserTwo.guardians.get(0);
        assertEquals(GuardianUser.GuardianUserStatus.INVALID, guardianUser.status);
    }

    @Test
    void canRemoveGuardian() {
        guardianUserThree.dependents.add(dependentUserThree.id);
        Persistence.otpUsers.replace(guardianUserThree.id, guardianUserThree);
        dependentUserThree.guardians.add(new GuardianUser(guardianUserThree.id, guardianUserThree.email, GuardianUser.GuardianUserStatus.CONFIRMED));
        Persistence.otpUsers.replace(dependentUserThree.id, dependentUserThree);
        dependentUserThree.delete(false);
        guardianUserThree = Persistence.otpUsers.getById(guardianUserThree.id);
        assertFalse(guardianUserThree.dependents.contains(dependentUserThree.id));
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
    private static void createMonitoredTripAsUser(OtpUser otpUser) throws Exception {
        MonitoredTrip monitoredTrip = new MonitoredTrip(OtpTestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = otpUser.id;

        // Set mock OTP responses so that trip existence checks in the
        // POST call below to save the monitored trip can pass.
        OtpTestUtils.setupOtpMocks(OtpTestUtils.createMockOtpResponsesForTripExistence());

        HttpResponseValues createTripResponse = mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(monitoredTrip),
            otpUser,
            HttpMethod.POST
        );

        // Reset mocks after POST, because the next call to this function will need it.
        // (The mocks will be also reset in the @AfterEach phase if there are any failures.)
        OtpTestUtils.resetOtpMocks();

        assertEquals(HttpStatus.OK_200, createTripResponse.status);
    }
}
