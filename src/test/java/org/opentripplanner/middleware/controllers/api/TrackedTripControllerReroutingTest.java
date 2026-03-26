package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.otp.response.Itinerary.getShortestDuration;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_ACTIVE;
import static org.opentripplanner.middleware.triptracker.ManageTripTracking.setOtpGraphQLVariables;

class TrackedTripControllerReroutingTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser soloOtpUser;
    private static OtpUser observerUser;
    private static TrackedJourney trackedJourney;
    private static Itinerary itinerary;
    private static Itinerary walkToVoterRegCenterItinerary;
    private static Itinerary walkFromBus40;

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    private static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    private static HashMap<String, String> headers;

    private MonitoredTrip monitoredTrip;

    @BeforeAll
    static void setUp() throws Exception {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);
        OtpTestUtils.mockOtpServer();

        itinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/adair-avenue-to-monroe-drive.json"),
            Itinerary.class
        );
        walkToVoterRegCenterItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-voter-reg-center.json"),
            Itinerary.class
        );
        walkFromBus40 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-from-bus-40.json"),
            Itinerary.class
        );

        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
        observerUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-observer-user"));
        soloOtpUser.relatedUsers = List.of(new RelatedUser(observerUser.email, RelatedUser.RelatedUserStatus.CONFIRMED));
        try {
            // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
            // from being able to use TEMP_AUTH0_USER_PASSWORD.
            var auth0User = Auth0Users.createAuth0UserForEmail(soloOtpUser.email, TEMP_AUTH0_USER_PASSWORD);
            soloOtpUser.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(soloOtpUser.id, soloOtpUser);
            headers = getMockHeaders(soloOtpUser);
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MonitoredTrip createMonitoredTrip(Itinerary itin) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = soloOtpUser.id;
        trip.itinerary = itin;
        // Original itinerary time should be populated.
        OtpGraphQLVariables params = new OtpGraphQLVariables();
        params.fromPlace = itin.legs.get(0).from.toCoordinates().getCoordinates();
        params.time = DateTimeUtils.convertToLocalDateTime(itin.startTime).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        trip.otp2QueryParams = params;
        trip.journeyState = new JourneyState();
        trip.journeyState.matchingItinerary = itin;
        // Original target date should be populated but does not really matter.
        trip.journeyState.targetDate = "2024-01-26";
        Persistence.monitoredTrips.create(trip);
        return trip;
    }

    @AfterAll
    static void tearDown() throws Exception {
        assumeTrue(IS_END_TO_END);
        DateTimeUtils.useSystemDefaultClockAndTimezone();
        restoreDefaultAuthDisabled();
        soloOtpUser = Persistence.otpUsers.getById(soloOtpUser.id);
        if (soloOtpUser != null) soloOtpUser.delete(true);
        observerUser = Persistence.otpUsers.getById(observerUser.id);
        if (observerUser != null) observerUser.delete(true);
    }

    @BeforeEach
    void beforeEachTest() {
        assumeTrue(IS_END_TO_END);
        monitoredTrip = createMonitoredTrip(itinerary);
    }

    @AfterEach
    void tearDownAfterTest() {
        assumeTrue(IS_END_TO_END);
        if (trackedJourney != null) {
            trackedJourney.delete();
            trackedJourney = null;
        }

        monitoredTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        if (monitoredTrip != null) monitoredTrip.delete();
    }

    @ParameterizedTest
    @MethodSource("rerouteTripTestCases")
    void canRerouteTrip(RerouteCase testData) throws Exception {
        assumeTrue(IS_END_TO_END);

        var mockOtpResponse = mockOtpReroutedPlanResponse(testData.reroutedResponse);
        var expectedReroutedItinerary = getShortestDuration(mockOtpResponse.get().plan.itineraries);
        ZonedDateTime reroutedStartTime = DateTimeUtils.makeOtpZonedDateTime(expectedReroutedItinerary.startTime);

        MonitoredTrip rerouteMonitoredTrip = monitoredTrip = createMonitoredTrip(testData.originalItinerary);
        rerouteMonitoredTrip.observers = soloOtpUser.relatedUsers;
        rerouteMonitoredTrip.leadTimeInMinutes = 10;
        rerouteMonitoredTrip.itineraryExistence = new ItineraryExistence();
        rerouteMonitoredTrip.itineraryExistence.setResultForDayOfWeek(
            new ItineraryExistence.ItineraryExistenceResult(),// Set the itinerary existence to the same day as the rerouted itinerary
            reroutedStartTime.getDayOfWeek()
        );
        rerouteMonitoredTrip.updateAllDaysOfWeek(true);
        Persistence.monitoredTrips.replace(rerouteMonitoredTrip.id, rerouteMonitoredTrip);

        var startTrackingPayload = new StartTrackingPayload();
        startTrackingPayload.tripId = rerouteMonitoredTrip.id;
        Step firstStep = testData.originalItinerary.legs.get(0).steps.get(0);
        startTrackingPayload.location = new TrackingLocation(Instant.now(), firstStep.lat, firstStep.lon);

        // Use current time relative to itinerary start (this will affect the computed target date after end tracking).
        DateTimeUtils.useFixedClockAt(
            ZonedDateTime.ofInstant(
                expectedReroutedItinerary.startTime.toInstant().plusSeconds(testData.offsetSeconds),
                DateTimeUtils.getOtpZoneId()
            )
        );

        ManageTripTracking.otpResponseProviderOverride = mockOtpResponse;
        var reroutingPoint = new Coordinates(expectedReroutedItinerary.legs.get(0).steps.get(2));
        var reroutingPointPosition = new TrackingLocation(Instant.now(), reroutingPoint.lat, reroutingPoint.lon);

        // Start tracking.
        var startTrackingResponse = startTracking(startTrackingPayload, HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        // Update tracking from a 'deviated' position.
        UpdatedTrackingPayload deviatedPositionPayload = createUpdateTrackingPayload(trackedJourney.id, List.of(testData.triggerLocation));
        var updateTrackingResponse = updateTracking(deviatedPositionPayload, HttpStatus.OK_200);
        // Confirm traveler is deemed 'deviated'.
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // Record the number of departed notifications
        long initialDepartedNotificationCount = pollDepartedNotificationCount(rerouteMonitoredTrip);
        assertEquals(1, initialDepartedNotificationCount);

        // Reroute trip from 'deviated' position (fetch the latest tracked journey data first).
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        var reroutedItinerary = ManageTripTracking.rerouteTrip(
            // Last parameter to TripTrackingData::ctor is not used for rerouting.
            new TripTrackingData(rerouteMonitoredTrip, trackedJourney, List.of())
        );
        assertEquals(expectedReroutedItinerary.duration, reroutedItinerary.duration);
        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertEquals(1, updated.reroutings.size());
        assertEquals(trackedJourney.longestConsecutiveDeviatedPoints, updated.longestConsecutiveDeviatedPoints);

        MonitoredTrip trip = Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id);
        assertEquals(expectedReroutedItinerary.duration, trip.reroutedItinerary.duration);
        assertEquals(expectedReroutedItinerary.duration, trip.journeyState.matchingItinerary.duration);
        assertEquals(expectedReroutedItinerary.legs.size(), trip.journeyState.matchingItinerary.legs.size());

        // Update tracking from start of rerouted position.
        updateTrackingResponse = updateTracking(deviatedPositionPayload, HttpStatus.OK_200);

        // Confirm traveler is no longer 'deviated'.
        assertNotEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // The current number of departed notifications should not have changed.
        assertEquals(initialDepartedNotificationCount, pollDepartedNotificationCount(rerouteMonitoredTrip));

        MonitoredTrip tripAfterRerouting = Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id);
        Itinerary beforeCheck = tripAfterRerouting.journeyState.matchingItinerary;

        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(
            tripAfterRerouting,
            // Returns the original itinerary or the rerouted itinerary based on the query param position.
            testData::getOtpResponse,
            new LegFinder()
        );
        testData.setVariableSupplier(checkMonitoredTrip::getQueryParamsForTargetZonedDateTime);
        checkMonitoredTrip.run();

        assertNotEquals(NEXT_TRIP_NOT_POSSIBLE, checkMonitoredTrip.journeyState.tripStatus);

        Itinerary afterCheck = Persistence.monitoredTrips.getById(tripAfterRerouting.id).journeyState.matchingItinerary;
        assertEquals(beforeCheck.duration, afterCheck.duration);

        // Reroute again from a different location.
        trackedJourney.locations.clear();
        trackedJourney.locations.add(reroutingPointPosition);
        reroutedItinerary = ManageTripTracking.rerouteTrip(
            new TripTrackingData(tripAfterRerouting, trackedJourney, List.of())
        );
        assertEquals(expectedReroutedItinerary.duration, reroutedItinerary.duration);
        updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertEquals(2, updated.reroutings.size());
        assertEquals(trackedJourney.longestConsecutiveDeviatedPoints, updated.longestConsecutiveDeviatedPoints);
        assertEquals(new Coordinates(updated.lastLocation()), reroutingPoint);

        // Update tracking from start of the new rerouted position.
        updateTrackingResponse = updateTracking(
            createUpdateTrackingPayload(trackedJourney.id, List.of(reroutingPointPosition)),
            HttpStatus.OK_200
        );
        // Confirm traveler is still not 'deviated'.
        assertNotEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // Stop tracking
        endTracking(trackedJourney.id);

        // Check that matching itinerary has been reset to original departure location
        MonitoredTrip resetTrip = Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id);
        assertEquals(
            rerouteMonitoredTrip.itinerary.legs.get(0).from.toCoordinates(),
            resetTrip.journeyState.matchingItinerary.legs.get(0).from.toCoordinates()
        );
        assertNull(resetTrip.reroutedItinerary);

        // Check that matching itinerary time corresponds to "today" if current time is before trip end,
        // or "tomorrow" if trip has ended (assuming a recurring trip).
        assertEquals(
            DateTimeUtils.nowAsZonedDateTime().plusDays(expectedReroutedItinerary.hasEnded() ? 1 : 0).toLocalDate().toString(),
            resetTrip.journeyState.targetDate
        );

        // Start tracking again from a random position. Traveler should be deviated.
        startTrackingResponse = startTracking(createStartTrackingPayload(), HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        // Note: Departed (and other notifications) are not being cleared when restarting live tracking.
        assertEquals(1, pollDepartedNotificationCount(rerouteMonitoredTrip));

        updateTrackingResponse = updateTracking(
            createUpdateTrackingPayload(trackedJourney.id, List.of(reroutingPointPosition)),
            HttpStatus.OK_200
        );
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);
    }

    private static Stream<RerouteCase> rerouteTripTestCases() {
        var deviatedPosition = new TrackingLocation(Instant.now(), 33.94412, -83.98899);
        return Stream.of(
            new RerouteCase(-300, deviatedPosition, walkToVoterRegCenterItinerary, OtpTestUtils.REROUTE_PLAN_RESPONSE),
            new RerouteCase(30, deviatedPosition, walkToVoterRegCenterItinerary, OtpTestUtils.REROUTE_PLAN_RESPONSE),
            new RerouteCase(3000, deviatedPosition, walkToVoterRegCenterItinerary, OtpTestUtils.REROUTE_PLAN_RESPONSE),
            new RerouteCase(30, deviatedPosition, walkFromBus40, OtpTestUtils.REROUTE_PLAN_RESPONSE)
        );
    }

    private static long pollDepartedNotificationCount(MonitoredTrip rerouteMonitoredTrip) {
        return Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id).journeyState.lastNotifications
            .stream()
            .filter(n -> n.type == NotificationType.DEPARTED_NOTIFICATION)
            .count();
    }

    /** Provides a mock OTP 'plan' rerouted response. */
    public static Supplier<OtpResponse> mockOtpReroutedPlanResponse(OtpDispatcherResponse dispatcherResponse) {
        return () -> {
            try {
                return dispatcherResponse.getResponse();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    void canBuildOtpGraphQLVariables() {
        Coordinates fromCoords = new Coordinates(33.94412, -83.98899);
        OtpGraphQLVariables originalTripVariables = new OtpGraphQLVariables();
        originalTripVariables.mobilityProfile = "mobility-profile";
        originalTripVariables.fromPlace = "from-place";
        originalTripVariables.toPlace = "33.9400633, -83.9854488";
        originalTripVariables.time = "08:36";
        OtpGraphQLVariables rerouteVariables = setOtpGraphQLVariables(originalTripVariables, fromCoords);
        assertEquals(originalTripVariables.mobilityProfile, rerouteVariables.mobilityProfile);
        assertEquals(originalTripVariables.toPlace, rerouteVariables.toPlace);
        assertEquals(fromCoords.getCoordinates(), rerouteVariables.fromPlace);
        assertNotEquals(originalTripVariables.time, rerouteVariables.time);
    }

    @Test
    void canGetTheLatestReroutingLocation() {
        TrackedJourney reroutedTrackedJourney = new TrackedJourney();
        reroutedTrackedJourney.reroutings.put("first-coords", DateTimeUtils.convertToDate(LocalDateTime.now().minusHours(1)));
        reroutedTrackedJourney.reroutings.put("last-coords", DateTimeUtils.convertToDate(LocalDateTime.now()));
        String lastReroutingLocation = reroutedTrackedJourney.getLastReroutingLocation();
        assertEquals("last-coords", lastReroutingLocation);
    }

    @Test
    void canCheckForRerouting() throws CloneNotSupportedException {
        monitoredTrip = createMonitoredTrip(itinerary);
        monitoredTrip.journeyState.tripStatus = TRIP_ACTIVE;
        Coordinates fromCoords = new Coordinates(33.94412, -83.98899);
        TrackedJourney reroutedTrackedJourney = new TrackedJourney();
        reroutedTrackedJourney.reroutings.put(fromCoords.getCoordinates(), DateTimeUtils.convertToDate(LocalDateTime.now()));
        reroutedTrackedJourney.tripId = monitoredTrip.id;
        Persistence.trackedJourneys.create(reroutedTrackedJourney);
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        OtpGraphQLVariables params = new OtpGraphQLVariables();
        params.fromPlace = "from-place";
        checkMonitoredTrip.checkForRerouting(params);
        assertEquals(fromCoords.getCoordinates(), params.fromPlace);
    }

    private StartTrackingPayload createStartTrackingPayload() {
        return createStartTrackingPayload(monitoredTrip.id);
    }

    private StartTrackingPayload createStartTrackingPayload(String monitorTripId) {
        var payload = new StartTrackingPayload();
        payload.tripId = monitorTripId;
        payload.location = new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, getDateAndConvertToSeconds());
        return payload;
    }

    private UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId, List<TrackingLocation> locations) {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = locations;
        return payload;
    }

    private EndTrackingPayload createEndTrackingPayload(String journeyId) {
        var payload = new EndTrackingPayload();
        payload.journeyId = journeyId;
        return payload;
    }

    /**
     * The mobile app sends timestamps in seconds which is then converted into milliseconds in {@link TripTrackingData}.
     * To represent this in testing, provide the time in seconds from epoch.
     */
    private static Date getDateAndConvertToSeconds() {
        return new Date(new Date().getTime() / 1000);
    }

    private TrackingResponse startTracking(StartTrackingPayload payload, int expectedStatus) throws JsonProcessingException {
        var response = makeRequest(START_TRACKING_TRIP_PATH, JsonUtils.toJson(payload), headers, HttpMethod.POST);
        assertEquals(expectedStatus, response.status);
        return JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
    }

    private void endTracking(String journeyId) throws JsonProcessingException {
        endTracking(END_TRACKING_TRIP_PATH, JsonUtils.toJson(createEndTrackingPayload(journeyId)));
    }

    private static void endTracking(String path, String payload) throws JsonProcessingException {
        var response = makeRequest(path, payload, headers, HttpMethod.POST);
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);
    }

    private TrackingResponse updateTracking(UpdatedTrackingPayload payload, int expectedStatus) throws JsonProcessingException {
        var response = makeRequest(UPDATE_TRACKING_TRIP_PATH, JsonUtils.toJson(payload), headers, HttpMethod.POST);
        assertEquals(expectedStatus, response.status);
        return JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
    }

    /**
     * Provides an OTP response based on a trip's query params.
     */
    private static class RerouteCase {
        public final int offsetSeconds;
        public final TrackingLocation triggerLocation;
        public final Itinerary originalItinerary;
        public final OtpDispatcherResponse reroutedResponse;
        private Supplier<OtpGraphQLVariables> variableSupplier;

        public RerouteCase(
            int offsetSeconds,
            TrackingLocation triggerLocation,
            Itinerary originalItinerary,
            OtpDispatcherResponse reroutedResponse
        ) {
            this.offsetSeconds = offsetSeconds;
            this.triggerLocation = triggerLocation;
            this.originalItinerary = originalItinerary;
            this.reroutedResponse = reroutedResponse;
        }

        public void setVariableSupplier(Supplier<OtpGraphQLVariables> supplier) {
            this.variableSupplier = supplier;
        }

        public OtpResponse getOtpResponse(OtpRequest ignored) {
            if (variableSupplier.get().fromPlace.endsWith(new Coordinates(triggerLocation).getCoordinates())) {
                return mockOtpReroutedPlanResponse(reroutedResponse).get();
            }
            OtpResponse response = new OtpResponse();
            response.plan = new TripPlan();
            response.plan.itineraries = List.of(originalItinerary);
            return response;
        }
    }
}
