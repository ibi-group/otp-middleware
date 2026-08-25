package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.users.User;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedDelete;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.deleteOtpUser;

/**
 * Tests to simulate getting trips as an OTP user with regular or enhanced admin credentials.
 * The following config parameters must be set in configurations/default/env.yml for these end-to-end tests to run:
 * - AUTH0_DOMAIN, AUTH0_API_CLIENT, AUTH0_API_SECRET set to valid values per <a href="https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow">...</a>.
 * The following environment variable must be set for these tests to run:
 * - RUN_E2E=true.
 */
class MonitoredTripControllerTest extends OtpMiddlewareTestEnvironment {
    private static AdminUser multiAdminUser;
    private static OtpUser soloOtpUser;
    private static OtpUser multiOtpUser;
    private static Bson soloUserFilter;
    private static final String MONITORED_TRIP_PATH = String.join("/", "api", MonitoredTripController.MONITORED_TRIP_PATH);
    private static final String DUMMY_STRING = "ABCDxyz";
    private static final String WED_2026_04_22 = "2026-04-22";
    public static final String MONITORED_TRIP_SOFT_DELETE_CONFIG = "MONITORED_TRIP_SOFT_DELETE";
    private static final boolean DEFAULT_TRIP_DELETE_MODE = MonitoredTripController.isSoftDelete();

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
        Persistence.deletedMonitoredTrips.removeFiltered(soloUserFilter);
        Persistence.deletedMonitoredTrips.removeFiltered(filterByUserId(multiOtpUser.id));
        ItineraryExistence.otpResponseProviderOverride = null;
        ConfigUtils.setConfigProperty(MONITORED_TRIP_SOFT_DELETE_CONFIG, Boolean.toString(DEFAULT_TRIP_DELETE_MODE));
    }

    /**
     * Create trips for two different {@link OtpUser} and attempt to get both trips using a user with 'enhanced' admin
     * credentials. We also add soft-deleted trips that should be excluded from query results.
     */
    @Test
    void canGetOwnMonitoredTrips() throws Exception {
        // Create a trip for the solo and the multi OTP user.
        persistNewMonitoredTripForUser(soloOtpUser);
        persistNewMonitoredTripForUser(multiOtpUser);

        // Create a soft-deleted trip for the solo and the multi OTP user.
        // The soft-deleted trips should not appear in query results at all, no matter how they got to that state.
        MonitoredTrip softDeletedTrip1 = persistSoftDeletedTripForUser(soloOtpUser);
        MonitoredTrip softDeletedTrip2 = persistSoftDeletedTripForUser(multiOtpUser);

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

        // Soft-deleted trips queried by id should not be found.
        assertEquals(HttpStatus.NOT_FOUND_404, requestMonitoredTripForUser(softDeletedTrip1.id, soloOtpUser).status);
        assertEquals(HttpStatus.NOT_FOUND_404, requestMonitoredTripForUser(softDeletedTrip2.id, multiOtpUser).status);
    }

    /**
     * Create trips for two different {@link OtpUser} and attempt to hard- or soft-delete them.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canDeleteOwnMonitoredTrips(boolean isSoftDelete) throws Exception {
        // Override config for soft/hard delete
        ConfigUtils.setConfigProperty(MONITORED_TRIP_SOFT_DELETE_CONFIG, Boolean.toString(isSoftDelete));

        // Create a trip for the solo and the multi OTP user.
        MonitoredTrip trip1 = persistNewMonitoredTripForUser(soloOtpUser);
        MonitoredTrip trip2 = persistNewMonitoredTripForUser(multiOtpUser);

        assertFalse(trip1.isDeleted);
        assertFalse(trip2.isDeleted);

        // Attempt to delete trip for each user.
        HttpResponseValues soloDeleteTripResponse = mockAuthenticatedDelete(MONITORED_TRIP_PATH + "/" + trip1.id, soloOtpUser);
        assertEquals(200, soloDeleteTripResponse.status);

        HttpResponseValues multiDeleteTripResponse = mockAuthenticatedDelete(MONITORED_TRIP_PATH + "/" + trip2.id, multiOtpUser);
        assertEquals(200, multiDeleteTripResponse.status);

        assertNull(Persistence.monitoredTrips.getById(trip1.id));
        assertNull(Persistence.monitoredTrips.getById(trip2.id));

        MonitoredTrip deletedTrip1 = Persistence.deletedMonitoredTrips.getById(trip1.id);
        MonitoredTrip deletedTrip2 = Persistence.deletedMonitoredTrips.getById(trip2.id);
        if (isSoftDelete) {
            assertNotNull(deletedTrip1);
            assertNotNull(deletedTrip2);
            assertTrue(deletedTrip1.isDeleted);
            assertTrue(deletedTrip2.isDeleted);
        } else {
            assertNull(deletedTrip1);
            assertNull(deletedTrip2);
        }
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
        ItineraryExistence.otpResponseProviderOverride = MonitoredTripControllerTest::fakeOtpResponse;

        // Create a trip for the solo OTP user.
        persistNewMonitoredTripForUser(soloOtpUser);

        MonitoredTrip originalTrip = Persistence.monitoredTrips.getOneFiltered(soloUserFilter);
        assertTrue(originalTrip.itineraryExistence.wednesday.validDates.isEmpty());

        // Mess up some fields in the trip. The modified field values should not appear in the persisted trip.
        originalTrip.tripName = DUMMY_STRING;
        originalTrip.otp2QueryParams.fromPlace = DUMMY_STRING;
        originalTrip.userId = DUMMY_STRING;

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
        // Messed up values should not have been modified. Test additional fields as needed.
        assertNotEquals(DUMMY_STRING, updatedTrip.tripName);
        assertNotEquals(DUMMY_STRING, updatedTrip.userId);
        assertNotEquals(DUMMY_STRING, updatedTrip.otp2QueryParams.fromPlace);
        // The persisted trip should have its existence overwritten with the one simulated above.
        assertNotNull(updatedTrip.itineraryExistence.id);
        assertNotEquals(originalTrip.itineraryExistence.id, updatedTrip.itineraryExistence.id);
        assertEquals(1, updatedTrip.itineraryExistence.wednesday.validDates.size());
        // No checks should have been added/cached.
        assertEquals(checksSize, MonitoredTripController.getChecksSize());
    }

    @Test
    void canCreateCheckForNewMonitoredTrip() throws Exception {
        ItineraryExistence.otpResponseProviderOverride = MonitoredTripControllerTest::fakeOtpResponse;

        // Create a trip for the solo OTP user without persisting.
        MonitoredTrip trip = createMonitoredTripForUser(soloOtpUser);

        int checksSize = MonitoredTripController.getChecksSize();

        // Make call to check itinerary existence.
        var result = mockAuthenticatedRequest(
            MONITORED_TRIP_PATH + CHECK_ITINERARY_SUBPATH,
            JsonUtils.toJson(trip),
            soloOtpUser,
            HttpMethod.POST
        );

        assertNull(Persistence.monitoredTrips.getOneFiltered(soloUserFilter));
        // A check should have been added/cached.
        assertEquals(checksSize + 1, MonitoredTripController.getChecksSize());

        // Check that we got data populated in the result.
        ItineraryExistence existence = JsonUtils.getPOJOFromJSON(result.responseBody, ItineraryExistence.class);
        Arrays.stream(DayOfWeek.values()).forEach(day -> {
            assertEquals(1, existence.getResultForDayOfWeek(day).validDates.size());
        });
    }

    @ParameterizedTest(name = "{3} Itinerary exists: {1}, Should save trip: {2}")
    @MethodSource("createMonitoredTripUsingPriorExistenceCheckCases")
    void canCreateMonitoredTripUsingPriorExistenceCheck(
        TripModifier tripArgs,
        boolean tripExists,
        boolean shouldSave,
        String message // used in test name above
    ) throws Exception {
        // Create a trip for the solo OTP user without persisting.
        MonitoredTrip trip = createMonitoredTripForUser(soloOtpUser);
        if (tripArgs.tripModifier != null) {
            tripArgs.tripModifier.accept(trip);
        }

        // Simulate a prior itinerary existence check for that trip.
        Itinerary verifiedItinerary = makeItinerary(DateTimeUtils.convertToDate(
            LocalDateTime.of(LocalDate.parse(WED_2026_04_22, DateTimeFormatter.ISO_LOCAL_DATE), LocalTime.MIDNIGHT)
        ));
        ItineraryExistence existence = new ItineraryExistence();
        existence.id = UUID.randomUUID().toString();
        if (tripExists) {
            existence.wednesday = new ItineraryExistence.ItineraryExistenceResult();
            existence.wednesday.validDates.add(WED_2026_04_22);
            existence.wednesday.itineraries = List.of(verifiedItinerary);
        }

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
        if (shouldSave) {
            assertNotNull(persistedTrip);
            // If applicable, the persisted trip should have its existence overwritten with the one simulated above.
            if (tripExists) {
                assertEquals(existence.id, persistedTrip.itineraryExistence.id);
                assertEquals(1, persistedTrip.itineraryExistence.wednesday.validDates.size());
                assertTrue(persistedTrip.itineraryExistence.wednesday.validDates.contains(WED_2026_04_22));
                assertEquals(verifiedItinerary.startTime, persistedTrip.itinerary.startTime);
            }
            // The previous itinerary check should have been removed.
            assertEquals(checksSize, MonitoredTripController.getChecksSize());
        } else {
            assertNull(persistedTrip);
            // The previous itinerary check should have been kept.
            assertEquals(checksSize + 1, MonitoredTripController.getChecksSize());
        }
    }

    private static Stream<Arguments> createMonitoredTripUsingPriorExistenceCheckCases() {
        return Stream.of(
            Arguments.of(
                new TripModifier(null),
                false,
                false,
                "Recurring trip monitored all days"
                ),
            Arguments.of(
                new TripModifier(trip -> {
                    trip.updateAllDaysOfWeek(false);
                    trip.wednesday = true;
                }),
                true,
                true,
                "Recurring trip monitored one day"
            ),
            Arguments.of(
                new TripModifier(trip -> {
                    trip.updateAllDaysOfWeek(false);
                }),
                true,
                true,
                "One-time trip, itinerary exists theoretically or temporarily thanks to favorable real-time conditions."
            ),
            // Allow persistence of one-trips where the itinerary existence check fails.
            // This is to be consistent with the OTP-react-redux UI that converts such trips to one-time.
            Arguments.of(
                new TripModifier(trip -> {
                    trip.updateAllDaysOfWeek(false);
                }),
                false,
                true,
                "One-time trip"
            )
        );
    }

    @Test
    void canCreateMonitoredTripWithoutPriorExistenceCheck() throws Exception {
        ItineraryExistence.otpResponseProviderOverride = MonitoredTripControllerTest::fakeOtpResponse;

        // Create a trip for the solo OTP user without persisting.
        MonitoredTrip trip = createMonitoredTripForUser(soloOtpUser);

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
    private static OtpResponse fakeOtpResponse(OtpRequest request) {
        OtpResponse response = new OtpResponse();
        response.plan = new TripPlan();
        response.plan.itineraries = List.of(makeItinerary(Date.from(request.dateTime.toInstant())));
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
     * Helper method to get a single trip for a user.
     */
    private HttpResponseValues requestMonitoredTripForUser(String tripId, OtpUser otpUser) throws Exception {
         return mockAuthenticatedGet(MONITORED_TRIP_PATH + "/" + tripId, otpUser);
    }

    /**
     * Creates a {@link MonitoredTrip} for the specified user.
     */
    private static MonitoredTrip persistNewMonitoredTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = createMonitoredTripForUser(otpUser);
        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    /**
     * Creates a soft-deleted {@link MonitoredTrip} for the specified user.
     */
    private static MonitoredTrip persistSoftDeletedTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = createMonitoredTripForUser(otpUser);
        monitoredTrip.id = UUID.randomUUID().toString();
        Persistence.deletedMonitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    private static MonitoredTrip createMonitoredTripForUser(OtpUser otpUser) {
        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = otpUser.id;
        monitoredTrip.otp2QueryParams = OtpTestUtils.getSampleQueryParams();
        monitoredTrip.otp2QueryParams.date = WED_2026_04_22;
        monitoredTrip.itinerary = makeItinerary(new Date());
        monitoredTrip.itineraryExistence = new ItineraryExistence();
        monitoredTrip.itineraryExistence.id = "itinerary-existence-id";
        monitoredTrip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.from = new Place();
        monitoredTrip.from.name = "From Place";
        monitoredTrip.to = new Place();
        monitoredTrip.to.name = "To Place";
        monitoredTrip.tripName = UUID.randomUUID().toString();
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

    /**
     * This class is needed to wrap a lambda. Arguments.of(...) does not accept lambdas.
     */
    private static class TripModifier {
        private final Consumer<MonitoredTrip> tripModifier;

        public TripModifier(Consumer<MonitoredTrip> tripModifier) {
            this.tripModifier = tripModifier;
        }
    }
}
