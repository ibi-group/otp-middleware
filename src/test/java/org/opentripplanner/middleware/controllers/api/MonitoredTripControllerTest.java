package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.users.User;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MobilityProfileLite;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.controllers.api.MonitoredTripController.CHECK_ITINERARY_SUBPATH;
import static org.opentripplanner.middleware.persistence.TypedPersistence.filterByUserId;
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
 * The following environment variable must be set for these tests to run: - RUN_E2E=true.
 *
 * Auth0 must be correctly configured as described here: https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow
 */
class MonitoredTripControllerTest extends OtpMiddlewareTestEnvironment {
    private static AdminUser multiAdminUser;
    private static OtpUser soloOtpUser;
    private static OtpUser multiOtpUser;
    private static Bson soloUserFilter;
    private static final String MONITORED_TRIP_PATH = String.join("/", "api", MonitoredTripController.MONITORED_TRIP_PATH);

    private static final String DUMMY_STRING = "ABCDxyz";

    /**
     * Create Otp and Admin user accounts. Create Auth0 account for just the Otp users. If
     * an Auth0 account is created for the admin user it will fail because the email address already exists.
     */
    @BeforeAll
    static void setUp() {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);

        String multiUserEmail = ApiTestUtils.generateEmailAddress("test-multiotpuser");
        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
        soloUserFilter = filterByUserId(soloOtpUser.id);
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
    static void tearDown() {
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
    void tearDownAfterTest() {
        Persistence.monitoredTrips.removeFiltered(soloUserFilter);
        Persistence.monitoredTrips.removeFiltered(filterByUserId(multiOtpUser.id));
        ItineraryExistence.otpResponseProviderOverride = null;
    }

    /**
     * Create trips for two different Otp users and attempt to get both trips with Otp user that has 'enhanced' admin
     * credentials.
     */
    @Test
    void canGetOwnMonitoredTrips() throws Exception {
        // Create a trip for the solo and the multi OTP user.
        persistNewMonitoredTripForUser(soloOtpUser);
        persistNewMonitoredTripForUser(multiOtpUser);

        // Get trips for solo Otp user.
        ResponseList<MonitoredTrip> soloTrips = getMonitoredTripsForUser(MONITORED_TRIP_PATH, soloOtpUser);
        // Expect only 1 trip for solo Otp user.
        assertEquals(1, soloTrips.data.size());

        // Get trips for multi Otp user/admin user.
        ResponseList<MonitoredTrip> multiTrips = getMonitoredTripsForUser(MONITORED_TRIP_PATH, multiOtpUser);

        // Multi Otp user has 'enhanced' admin credentials, still expect only 1 trip to be returned as the scope will
        // limit the requesting user to a single 'otp-user' user type.
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
        persistNewMonitoredTripForUser(soloOtpUser);

        // Expect only 1 trip for solo Otp user.
        assertEquals(1, Persistence.monitoredTrips.getCountFiltered(soloUserFilter));

        MonitoredTrip originalTrip = Persistence.monitoredTrips.getOneFiltered(soloUserFilter);
        assertNotNull(originalTrip.itinerary);
        assertNotNull(originalTrip.itineraryExistence);
        // Can't really assert journeyState because itinerary checks will not be run for these tests.
        assertNotEquals(DUMMY_STRING, originalTrip.userId);
        assertNotNull(originalTrip.from);
        assertNotNull(originalTrip.to);

        MonitoredTrip modifiedTrip = new MonitoredTrip();
        modifiedTrip.id = originalTrip.id;
        modifiedTrip.otp2QueryParams = OtpTestUtils.getSampleQueryParams();
        modifiedTrip.userId = DUMMY_STRING;
        modifiedTrip.itineraryExistence = new ItineraryExistence();
        modifiedTrip.itineraryExistence.id = DUMMY_STRING;
        modifiedTrip.itineraryExistence.thursday = new ItineraryExistence.ItineraryExistenceResult();

        mockAuthenticatedRequest(
            MONITORED_TRIP_PATH,
            JsonUtils.toJson(modifiedTrip),
            soloOtpUser,
            HttpMethod.PUT
        );

        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(originalTrip.id);
        assertEquals(originalTrip.itinerary.startTime, updatedTrip.itinerary.startTime);
        assertEquals(originalTrip.otp2QueryParams.time, updatedTrip.otp2QueryParams.time);
        assertEquals(originalTrip.userId, updatedTrip.userId);
        assertEquals(originalTrip.from.name, updatedTrip.from.name);
        assertEquals(originalTrip.to.name, updatedTrip.to.name);
        assertEquals(originalTrip.itineraryExistence.id, updatedTrip.itineraryExistence.id);
        assertNull(updatedTrip.itineraryExistence.thursday);
        assertNotNull(updatedTrip.itineraryExistence.wednesday);
    }

    @Test
    void canUpdateMonitoredTripAfterRecheckingExistence() throws Exception {
        ItineraryExistence.otpResponseProviderOverride = this::fakeOtpResponse;

        // Create a trip for the solo OTP user.
        persistNewMonitoredTripForUser(soloOtpUser);

        MonitoredTrip originalTrip = Persistence.monitoredTrips.getOneFiltered(soloUserFilter);
        assertTrue(originalTrip.itineraryExistence.wednesday.validDates.isEmpty());

        int checksSize = MonitoredTripController.getChecksSize();

        // Make call to check itinerary existence.
        mockAuthenticatedRequest(
            MONITORED_TRIP_PATH + CHECK_ITINERARY_SUBPATH,
            JsonUtils.toJson(originalTrip),
            soloOtpUser,
            HttpMethod.POST
        );

        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getOneFiltered(soloUserFilter);
        assertNotNull(updatedTrip);
        // The persisted trip should have its existence overwritten with the one simulated above.
        assertNotNull(updatedTrip.itineraryExistence.id);
        assertNotEquals(originalTrip.itineraryExistence.id, updatedTrip.itineraryExistence.id);
        assertEquals(1, updatedTrip.itineraryExistence.wednesday.validDates.size());
        // No checks should have been added/cached.
        assertEquals(checksSize, MonitoredTripController.getChecksSize());

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canCreateMonitoredTripUsingPriorExistenceCheck(boolean monitorAllDays) throws Exception {
        final String WED_2026_04_22 = "2026-04-22";
        // Create a trip for the solo OTP user without persisting.
        MonitoredTrip trip = createMonitoredTripForUser(soloOtpUser);
        trip.tripName = UUID.randomUUID().toString();
        trip.otp2QueryParams.date = WED_2026_04_22;
        trip.itinerary.startTime = Date.from(Instant.ofEpochMilli(0));
        if (!monitorAllDays) {
            trip.updateAllDaysOfWeek(false);
            trip.wednesday = true;
        }

        // Simulate a prior itinerary existence check for that trip.
        ItineraryExistence existence = new ItineraryExistence();
        existence.id = UUID.randomUUID().toString();
        existence.wednesday = new ItineraryExistence.ItineraryExistenceResult();
        existence.wednesday.validDates.add(WED_2026_04_22);
        Itinerary verifiedItinerary = makeItinerary(DateTimeUtils.convertToDate(
            LocalDateTime.of(LocalDate.parse(WED_2026_04_22, DateTimeFormatter.ISO_LOCAL_DATE), LocalTime.MIDNIGHT)
        ));
        existence.wednesday.itineraries = List.of(verifiedItinerary);

        int checksSize = MonitoredTripController.getChecksSize();
        MonitoredTripController.simulateExistenceCheck(existence);
        assertEquals(checksSize + 1, MonitoredTripController.getChecksSize());

        // Add existence id to the trip above
        trip.itineraryExistence.id = existence.id;
        assertTrue(trip.itineraryExistence.wednesday.validDates.isEmpty());

        // Make call to persist the trip.
        mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(trip),
            soloOtpUser,
            HttpMethod.POST
        );

        MonitoredTrip persistedTrip = Persistence.monitoredTrips.getOneFiltered(eq("tripName", trip.tripName));
        if (monitorAllDays) {
            assertNull(persistedTrip);
            // The previous itinerary check should have been kept.
            assertEquals(checksSize + 1, MonitoredTripController.getChecksSize());
        } else {
            assertNotNull(persistedTrip);
            // The persisted trip should have its existence overwritten with the one simulated above.
            assertEquals(existence.id, persistedTrip.itineraryExistence.id);
            assertEquals(1, persistedTrip.itineraryExistence.wednesday.validDates.size());
            assertTrue(persistedTrip.itineraryExistence.wednesday.validDates.contains(WED_2026_04_22));
            assertEquals(verifiedItinerary.startTime, persistedTrip.itinerary.startTime);
            // The previous itinerary check should have been removed.
            assertEquals(checksSize, MonitoredTripController.getChecksSize());
        }
    }

    @Test
    void canCreateMonitoredTripWithoutPriorExistenceCheck() throws Exception {
        ItineraryExistence.otpResponseProviderOverride = this::fakeOtpResponse;

        final String WED_2026_04_22 = "2026-04-22";
        // Create a trip for the solo OTP user without persisting.
        MonitoredTrip trip = createMonitoredTripForUser(soloOtpUser);
        trip.tripName = UUID.randomUUID().toString();
        trip.otp2QueryParams.date = WED_2026_04_22;
        trip.itinerary.startTime = Date.from(Instant.ofEpochMilli(0));

        // Make call to persist the trip.
        mockAuthenticatedRequest(MONITORED_TRIP_PATH,
            JsonUtils.toJson(trip),
            soloOtpUser,
            HttpMethod.POST
        );

        MonitoredTrip persistedTrip = Persistence.monitoredTrips.getOneFiltered(eq("tripName", trip.tripName));
        assertNotNull(persistedTrip);
        ZonedDateTime tripTime = ItineraryUtils.getDatesToCheckItineraryExistence(trip).get(0);
        assertEquals(Date.from(tripTime.toInstant()), persistedTrip.itinerary.startTime);
    }

    /**
     * Fake OTP response provider whose itineraries always match the original itinerary of the test.
     */
    private OtpResponse fakeOtpResponse(OtpRequest request) {
        Itinerary itinerary = makeItinerary(Date.from(request.dateTime.toInstant()));
        OtpResponse response = new OtpResponse();
        response.plan = new TripPlan();
        response.plan.itineraries = List.of(itinerary);
        return response;
    }

    /**
     * Creates a trivial itinerary with one leg, with the start and end date of the itinerary and leg the same.
     */
    private static Itinerary makeItinerary(Date date) {
        Itinerary itinerary = new Itinerary();
        itinerary.startTime = itinerary.endTime = date;
        Leg leg = new Leg();
        leg.startTime = leg.endTime = date;
        itinerary.legs = List.of(leg);
        return itinerary;
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
    private static void persistNewMonitoredTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = createMonitoredTripForUser(otpUser);
        Persistence.monitoredTrips.create(monitoredTrip);
    }

    private static MonitoredTrip createMonitoredTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = otpUser.id;
        monitoredTrip.otp2QueryParams = OtpTestUtils.getSampleQueryParams();
        monitoredTrip.itinerary = makeItinerary(new Date());
        monitoredTrip.itineraryExistence = new ItineraryExistence();
        monitoredTrip.itineraryExistence.id = "itinerary-existence-id";
        monitoredTrip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.from = new Place();
        monitoredTrip.from.name = "From Place";
        monitoredTrip.to = new Place();
        monitoredTrip.to.name = "To Place";
        return monitoredTrip;
    }

    @Test
    void canGetSharedTrips() throws Exception {
        MonitoredTrip ownTrip = new MonitoredTrip();
        ownTrip.id = "shared-trips-own-trip";
        ownTrip.userId = soloOtpUser.id;

        RelatedUser companion = new RelatedUser();
        companion.email = "companion@example.com";

        RelatedUser soloAsCompanion = new RelatedUser();
        soloAsCompanion.email = soloOtpUser.email;

        MonitoredTrip ownTripWithCompanion = new MonitoredTrip();
        ownTripWithCompanion.id = "shared-trips-own-trip-with-companion";
        ownTripWithCompanion.companion = companion;
        ownTripWithCompanion.userId = soloOtpUser.id;

        MonitoredTrip ownTripWithObservers = new MonitoredTrip();
        ownTripWithObservers.id = "shared-trips-own-trip-with-observers";
        ownTripWithObservers.observers = List.of(companion);
        ownTripWithObservers.userId = soloOtpUser.id;

        MobilityProfileLite soloAsPrimary = new MobilityProfileLite();
        soloAsPrimary.userId = soloOtpUser.id;

        MobilityProfileLite otherAsPrimary = new MobilityProfileLite();
        otherAsPrimary.userId = multiOtpUser.id;

        MonitoredTrip ownTripForDependent = new MonitoredTrip();
        ownTripForDependent.id = "shared-trips-own-trip-for-dependent";
        ownTripForDependent.primary = otherAsPrimary;
        ownTripForDependent.userId = soloOtpUser.id;

        MonitoredTrip otherTrip = new MonitoredTrip();
        otherTrip.id = "shared-trips-other-trip";
        otherTrip.userId = multiOtpUser.id;

        MonitoredTrip otherTripWithSoloAsDependent = new MonitoredTrip();
        otherTripWithSoloAsDependent.id = "shared-trips-other-trip-solo-primary";
        otherTripWithSoloAsDependent.primary = soloAsPrimary;
        otherTripWithSoloAsDependent.userId = multiOtpUser.id;

        MonitoredTrip otherTripWithSoloAsCompanion = new MonitoredTrip();
        otherTripWithSoloAsCompanion.id = "shared-trips-other-trip-solo-companion";
        otherTripWithSoloAsCompanion.companion = soloAsCompanion;
        otherTripWithSoloAsCompanion.userId = multiOtpUser.id;

        MonitoredTrip otherTripWithSoloAsObserver = new MonitoredTrip();
        otherTripWithSoloAsObserver.id = "shared-trips-other-trip-solo-observer";
        otherTripWithSoloAsObserver.observers = List.of(soloAsCompanion);
        otherTripWithSoloAsObserver.userId = multiOtpUser.id;

        List<MonitoredTrip> trips = List.of(
            ownTrip,
            ownTripForDependent,
            ownTripWithCompanion,
            ownTripWithObservers,
            otherTrip,
            otherTripWithSoloAsDependent,
            otherTripWithSoloAsCompanion,
            otherTripWithSoloAsObserver
        );
        trips.forEach(Persistence.monitoredTrips::create);

        List<MonitoredTrip> fetchedTrips = getMonitoredTripsForUser(MONITORED_TRIP_PATH, soloOtpUser).data;
        assertEquals(trips.size() - 1, fetchedTrips.size());

        Set<String> ids = trips.stream().map(t -> t.id).collect(Collectors.toSet());
        ids.remove(otherTrip.id);
        Set<String> fetchedIds = fetchedTrips.stream().map(t -> t.id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(fetchedIds));
    }
}
