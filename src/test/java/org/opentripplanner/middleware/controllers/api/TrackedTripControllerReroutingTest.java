package org.opentripplanner.middleware.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.TrackedTripTestContext;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_ACTIVE;
import static org.opentripplanner.middleware.triptracker.ManageTripTracking.setOtpGraphQLVariables;

class TrackedTripControllerReroutingTest extends OtpMiddlewareTestEnvironment {

    private static TrackedTripTestContext context;
    private static OtpUser observerUser;
    private static Itinerary itinerary;
    private static Itinerary walkToVoterRegCenterItinerary;
    private static Itinerary walkFromBus40;

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

        context = new TrackedTripTestContext();
        observerUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-observer-user"));
        context.otpUser.relatedUsers = List.of(new RelatedUser(observerUser.email, RelatedUser.RelatedUserStatus.CONFIRMED));
    }

    private static MonitoredTrip createMonitoredTrip(Itinerary itin) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = context.otpUser.id;
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
    static void tearDown() {
        assumeTrue(IS_END_TO_END);
        DateTimeUtils.useSystemDefaultClockAndTimezone();
        restoreDefaultAuthDisabled();
        context.tearDown();
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
        rerouteMonitoredTrip.observers = context.otpUser.relatedUsers;
        rerouteMonitoredTrip.leadTimeInMinutes = 10;
        rerouteMonitoredTrip.itineraryExistence = new ItineraryExistence();
        // Set the itinerary existence to the same day as the rerouted itinerary
        rerouteMonitoredTrip.itineraryExistence.setResultForDayOfWeek(
            new ItineraryExistence.ItineraryExistenceResult(),
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
        TrackingResponse journey1response = context.startTracking(startTrackingPayload, HttpStatus.OK_200);
        String journey1Id = journey1response.journeyId;

        // Update tracking from a 'deviated' position.
        var updateTrackingResponse = context.updateTracking(journey1Id, List.of(testData.triggerLocation), HttpStatus.OK_200);
        // Confirm traveler is deemed 'deviated'.
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // Record the number of departed notifications
        long initialDepartedNotificationCount = pollDepartedNotificationCount(rerouteMonitoredTrip);
        assertEquals(1, initialDepartedNotificationCount);

        // Reroute trip from 'deviated' position (fetch the latest tracked journey data first).
        TrackedJourney journey1refetched = Persistence.trackedJourneys.getById(journey1Id);
        var reroutedItinerary = ManageTripTracking.rerouteTrip(
            // Last parameter to TripTrackingData::ctor is not used for rerouting.
            new TripTrackingData(rerouteMonitoredTrip, journey1refetched, List.of())
        );
        assertEquals(expectedReroutedItinerary.duration, reroutedItinerary.duration);
        TrackedJourney journey1updated = Persistence.trackedJourneys.getById(journey1Id);
        assertEquals(1, journey1updated.reroutings.size());
        assertEquals(journey1refetched.longestConsecutiveDeviatedPoints, journey1updated.longestConsecutiveDeviatedPoints);

        MonitoredTrip trip = Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id);
        assertEquals(expectedReroutedItinerary.duration, trip.reroutedItinerary.duration);
        assertEquals(expectedReroutedItinerary.duration, trip.journeyState.matchingItinerary.duration);
        assertEquals(expectedReroutedItinerary.legs.size(), trip.journeyState.matchingItinerary.legs.size());

        // Update tracking from start of rerouted position.
        updateTrackingResponse = context.updateTracking(journey1Id, List.of(testData.triggerLocation), HttpStatus.OK_200);

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
        journey1refetched.locations.clear();
        journey1refetched.locations.add(reroutingPointPosition);
        reroutedItinerary = ManageTripTracking.rerouteTrip(
            new TripTrackingData(tripAfterRerouting, journey1refetched, List.of())
        );
        assertEquals(expectedReroutedItinerary.duration, reroutedItinerary.duration);
        journey1updated = Persistence.trackedJourneys.getById(journey1Id);
        assertEquals(2, journey1updated.reroutings.size());
        assertEquals(journey1refetched.longestConsecutiveDeviatedPoints, journey1updated.longestConsecutiveDeviatedPoints);
        assertEquals(new Coordinates(journey1updated.lastLocation()), reroutingPoint);

        // Update tracking from start of the new rerouted position.
        updateTrackingResponse = context.updateTracking(journey1Id, List.of(reroutingPointPosition), HttpStatus.OK_200);
        // Confirm traveler is still not 'deviated'.
        assertNotEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // Stop tracking
        context.endTracking(journey1Id);

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
        TrackingResponse journey2response = context.startTracking(monitoredTrip.id, HttpStatus.OK_200);

        // Note: Departed (and other notifications) are not being cleared when restarting live tracking.
        assertEquals(1, pollDepartedNotificationCount(rerouteMonitoredTrip));

        updateTrackingResponse = context.updateTracking(journey2response.journeyId, List.of(reroutingPointPosition), HttpStatus.OK_200);
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
