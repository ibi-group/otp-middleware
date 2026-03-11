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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
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
import org.opentripplanner.middleware.triptracker.TraceData;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.triptracker.instruction.ContinueInstruction;
import org.opentripplanner.middleware.triptracker.instruction.DeviatedInstruction;
import org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.TrackPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.models.TrackedJourney.FORCIBLY_TERMINATED;
import static org.opentripplanner.middleware.models.TrackedJourney.TERMINATED_BY_USER;
import static org.opentripplanner.middleware.otp.response.Itinerary.getShortestDuration;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_ACTIVE;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversalTest.WALK_AND_TRANSIT_LEG_OVERLAP_POINT;
import static org.opentripplanner.middleware.triptracker.ManageTripTracking.setOtpGraphQLVariables;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

class TrackedTripControllerTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser soloOtpUser;
    private static OtpUser observerUser;
    private static TrackedJourney trackedJourney;
    private static Itinerary itinerary;
    private static Itinerary multiLegItinerary;
    private static Itinerary walkToVoterRegCenterItinerary;
    private static Itinerary walkToBus20;
    private static Itinerary walkToBus12;
    private static Itinerary arrivingOnBus40;
    private static Itinerary walkFromBus40;

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    private static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    private static final String TRACK_TRIP_PATH = ROUTE_PATH + "track";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    private static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";
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
        multiLegItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/27nb-midtown-to-ansley.json"),
            Itinerary.class
        );
        walkToVoterRegCenterItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-voter-reg-center.json"),
            Itinerary.class
        );
        walkToBus20 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-20.json"),
            Itinerary.class
        );
        walkToBus12 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-12.json"),
            Itinerary.class
        );
        arrivingOnBus40 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/bus-40-to-dest-away-from-sidewalk.json"),
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

    @Test
    void canCompleteJourneyLifeCycle() throws Exception {
        assumeTrue(IS_END_TO_END);

        var startTrackingResponse = startTracking(createStartTrackingPayload(), HttpStatus.OK_200);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripStatus.DEVIATED.name(), startTrackingResponse.tripStatus);

        String journeyId = startTrackingResponse.journeyId;
        trackedJourney = Persistence.trackedJourneys.getById(journeyId);
        // A single location is submitted when starting tracking.
        assertEquals(1, trackedJourney.locations.size());
        assertEquals(TripStatus.DEVIATED, trackedJourney.lastLocation().tripStatus);

        var updateTrackingResponse = updateTracking(createUpdateTrackingPayload(journeyId), HttpStatus.OK_200);
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);
        assertNotEquals(0, updateTrackingResponse.frequencySeconds);
        assertNotNull(updateTrackingResponse.journeyId);

        trackedJourney = Persistence.trackedJourneys.getById(journeyId);
        // The call to updatetracking sent 3 additional locations, so there are 4 locations stored at this point.
        assertEquals(4, trackedJourney.locations.size());
        assertEquals(trackedJourney.locations.get(3), trackedJourney.lastLocation());
        assertEquals(TripStatus.DEVIATED, trackedJourney.lastLocation().tripStatus);

        endTracking(journeyId);

        // Check that the TrackedJourney Mongo record has been updated.
        TrackedJourney mongoTrackedJourney = Persistence.trackedJourneys.getById(journeyId);
        assertEquals(TERMINATED_BY_USER, mongoTrackedJourney.endCondition);
        assertNotEquals(-1, mongoTrackedJourney.longestConsecutiveDeviatedPoints);
        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    @Test
    void canNotRestartAnOngoingJourney() throws Exception {
        assumeTrue(IS_END_TO_END);
        
        // Make two identical requests to start and update a journey. The second one should fail.
        for (int i = 0; i < 2; i++) {
            var response = startTracking(
                createStartTrackingPayload(),
                i == 0 ? HttpStatus.OK_200 : HttpStatus.FORBIDDEN_403
            );
            if (i == 0) {
                trackedJourney = Persistence.trackedJourneys.getById(response.journeyId);
            } else {
                assertEquals(
                    "A journey of this trip has already been started. End the current journey before starting another.",
                    response.message
                );
            }
        }
    }

    @Test
    void canStartThenUpdateOngoingJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        Leg firstLeg = itinerary.legs.get(0);
        Coordinates coords = new Coordinates(firstLeg.steps.get(0));
        String jsonPayload = JsonUtils.toJson(createTrackPayload(coords));

        // Make two identical requests to start and update a journey. Record outcomes to see if they are same.
        TrackingResponse[] trackResponses = new TrackingResponse[2];
        for (int i = 0; i < 2; i++) {
            var response = makeRequest(TRACK_TRIP_PATH, jsonPayload, headers, HttpMethod.POST);
            assertEquals(HttpStatus.OK_200, response.status);

            var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
            trackResponses[i] = trackResponse;
            assertNotEquals(0, trackResponse.frequencySeconds);
            assertNotNull(trackResponse.journeyId);

            if (trackedJourney == null) {
                trackedJourney = Persistence.trackedJourneys.getById(trackResponse.journeyId);
            }
        }

        assertEquals(trackResponses[0].instruction, trackResponses[1].instruction);
        assertEquals(trackResponses[0].tripStatus, trackResponses[1].tripStatus);
        assertEquals(trackResponses[0].journeyId, trackResponses[1].journeyId);
    }

    @ParameterizedTest
    @MethodSource("createInstructionAndStatusCases")
    void canGenerateInstructionAndStatus(
        String message,
        Itinerary itinerary,
        TraceData traceData
    ) throws Exception {
        assumeTrue(IS_END_TO_END);

        monitoredTrip = createMonitoredTrip(itinerary);

        // Defaults to itinerary start time, unless specified otherwise.
        Instant instant = monitoredTrip.itinerary.startTime.toInstant();
        if (traceData.instant != null) {
            instant = traceData.instant;
        }

        String jsonPayload = JsonUtils.toJson(
            createTrackPayload(
                monitoredTrip,
                traceData.position,
                traceData.speed,
                // The timestamp has to be in seconds, hence the division by 1000.
                Date.from(Instant.ofEpochMilli(instant.toEpochMilli() / 1000))
            )
        );

        // Make a request to start a journey.
        var response = makeRequest(TRACK_TRIP_PATH, jsonPayload, headers, HttpMethod.POST);

        assertEquals(HttpStatus.OK_200, response.status);
        var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals(traceData.expectedInstruction, trackResponse.instruction, message);
        assertEquals(traceData.tripStatus.name(), trackResponse.tripStatus);
        assertNotNull(trackResponse.journeyId);
        trackedJourney = Persistence.trackedJourneys.getById(trackResponse.journeyId);

        // Check that deviation fields get computed and recorded.
        Double deviationMeters = trackedJourney.lastLocation().deviationMeters;
        assertNotNull(deviationMeters);
        assertNotEquals(0, deviationMeters);

        // Second request to update a journey
        response = makeRequest(TRACK_TRIP_PATH, jsonPayload, headers, HttpMethod.POST);

        assertEquals(HttpStatus.OK_200, response.status);
        trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertNotEquals(0, trackResponse.frequencySeconds);
        assertEquals(traceData.expectedInstruction, trackResponse.instruction, message);
        assertNotNull(trackResponse.journeyId);
        assertEquals(trackedJourney.id, trackResponse.journeyId);
    }

    private static WaitForTransitInstruction waitForBusIsntruction(int waitMinutes) {
        Leg multiItinBusLeg = multiLegItinerary.legs.get(multiLegItinerary.legs.size() - 2);
        return new WaitForTransitInstruction(
            multiItinBusLeg,
            multiItinBusLeg.getScheduledStartTime().toInstant().minus(Duration.ofMinutes(waitMinutes)),
            Locale.US
        );
    }

    private static Stream<Arguments> createInstructionAndStatusCases() {
        final int NORTH_WEST_BEARING = 315;
        final int NORTH_EAST_BEARING = 45;
        final int WEST_BEARING = 270;
        final Locale locale = Locale.US;

        Leg firstLeg = itinerary.legs.get(0);
        Step adairAvenueNortheastStep = firstLeg.steps.get(0);
        Step ponceDeLeonPlaceNortheastStep = firstLeg.steps.get(2);
        Coordinates firstStepCoords = new Coordinates(adairAvenueNortheastStep);
        Coordinates thirdStepCoords = new Coordinates(ponceDeLeonPlaceNortheastStep);
        Coordinates destinationCoords = new Coordinates(firstLeg.to);
        String monroeDrDestinationName = firstLeg.to.name;

        Leg multiItinFirstLeg = multiLegItinerary.legs.get(0);
        Leg multiItinLastLeg = multiLegItinerary.legs.get(multiLegItinerary.legs.size() - 1);
        Leg multiItinBusLeg = multiLegItinerary.legs.get(multiLegItinerary.legs.size() - 2);
        Coordinates multiItinFirstLegDestCoords = new Coordinates(multiItinFirstLeg.to);
        Coordinates multiItinLastLegDestCoords = new Coordinates(multiItinLastLeg.to);
        String ansleyMallPetShopDestinationName = multiItinLastLeg.to.name;

        Coordinates pointNearEndOfSidewalk = new Coordinates(33.958954, -84.006451);
        Coordinates pointPastEndOfSidewalk = new Coordinates(33.958917, -84.006521);

        WaitForTransitInstruction multiItinWaitForTransitInstruction = waitForBusIsntruction(6);
        return Stream.of(
            Arguments.of(
                "Coords near first step should produce relevant instruction",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(firstStepCoords, 1, NORTH_EAST_BEARING))
                    .withTripStatus(TripStatus.ON_SCHEDULE)
                    .withExpectedInstruction(new OnTrackInstruction(1, adairAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Coords in the 'upcoming' range of first step should produce relevant instruction and deemed not deviated.",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(firstStepCoords, 4, NORTH_EAST_BEARING))
                    .withTripStatus(TripStatus.ON_SCHEDULE)
                    .withExpectedInstruction(new OnTrackInstruction(4, adairAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Deviated coords near first step should produce instruction to head to first step #1",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(firstStepCoords, 30, NORTH_EAST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "Deviated coords near first step should produce instruction to head to first step #2",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(firstStepCoords, 15, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "Coords along a step should produce a continue on street instruction",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(firstStepCoords, 20, WEST_BEARING))
                    .withTripStatus(TripStatus.ON_SCHEDULE)
                    .withExpectedInstruction(new ContinueInstruction(adairAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Coords near a not-first step should produce relevant instruction",
                itinerary,
                new TraceData()
                    .withPosition(thirdStepCoords)
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(new OnTrackInstruction(0, ponceDeLeonPlaceNortheastStep, locale))
            ),
            Arguments.of(
                "Deviated coords near a not-first step should produce instruction to head to step",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(thirdStepCoords, 30, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "Instructions for destination coordinate",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(destinationCoords, 1, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction(new OnTrackInstruction(2, monroeDrDestinationName, locale))
            ),
            Arguments.of(
                "Arriving ahead of schedule to a bus stop at the end of first leg.",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinFirstLegDestCoords, 1.5, WEST_BEARING))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(multiItinWaitForTransitInstruction)
            ),
            // This position overlaps with the beginning of the transit trip,
            // but it is still within the 'upcoming' radius of the stop, so display a "wait for transit" instruction.
            Arguments.of(
                "Arriving ahead of schedule to a bus stop at the end of first leg should produce a non-trivial instruction.",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinFirstLegDestCoords, 1.5, NORTH_EAST_BEARING))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(multiItinWaitForTransitInstruction)
            ),
            Arguments.of(
                "Arriving ahead of schedule near a bus stop (in 'upcoming' range) at the end of first leg.",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinFirstLegDestCoords, 7, WEST_BEARING))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(multiItinWaitForTransitInstruction)
            ),
            Arguments.of(
                "Instructions for destination coordinate of multi-leg trip",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinLastLegDestCoords, 1, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction(new OnTrackInstruction(1, ansleyMallPetShopDestinationName, locale))
            ),
            Arguments.of(
                "Arrival instruction when destination is away from sidewalk",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(pointNearEndOfSidewalk)
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction("Your destination is in the vicinity.")
            ),
            Arguments.of(
                "Arrival instruction when destination is away from sidewalk",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(pointPastEndOfSidewalk)
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction("Your destination is in the vicinity.")
            ),
            Arguments.of(
                "Arriving at bus stop within 2 minutes of bus departure (in 'upcoming' range) should put traveler ahead of, not behind schedule.",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinFirstLegDestCoords, 7, WEST_BEARING))
                    .withInstant(multiItinBusLeg.startTime.toInstant().minus(2, ChronoUnit.MINUTES))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(waitForBusIsntruction(2))
            ),
            Arguments.of(
                "Arriving ahead of schedule near a bus stop (in 'upcoming' range) at the end of first leg.",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinFirstLegDestCoords, 7, WEST_BEARING))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction(multiItinWaitForTransitInstruction)
            ),
            Arguments.of(
                "Instructions for destination coordinate of multi-leg trip",
                multiLegItinerary,
                new TraceData()
                    .withPosition(createPoint(multiItinLastLegDestCoords, 1, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction(new OnTrackInstruction(1, ansleyMallPetShopDestinationName, locale))
            ),
            Arguments.of(
                "Arrival instruction when destination is away from sidewalk",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(pointNearEndOfSidewalk)
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction("Your destination is in the vicinity.")
            ),
            Arguments.of(
                "Arrival instruction when destination is away from sidewalk",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(pointPastEndOfSidewalk)
                    .withTripStatus(TripStatus.COMPLETED)
                    .withExpectedInstruction("Your destination is in the vicinity.")
            ),
            Arguments.of(
                "Arrival at bus stop instruction when bus stop farther from end-of-leg",
                walkToBus12,
                new TraceData()
                    .withPosition(new Coordinates(33.78118173054279, -84.3867252767086))
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction("Your bus stop is in the vicinity.")
            ),
            Arguments.of(
                "Deviated significantly from nearest step should still produce walk instruction",
                itinerary,
                new TraceData()
                    .withPosition(createPoint(thirdStepCoords, 1000, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction("Head to Kanuga Street Northeast")
            ),
            Arguments.of(
                "Standing at location where walk leg and start of transit leg overlap, should produce walk instruction",
                walkToBus20,
                new TraceData()
                    .withPosition(WALK_AND_TRANSIT_LEG_OVERLAP_POINT)
                    .withSpeed(0)
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction("Head to crossing over Longmire Way")
            ),
            Arguments.of(
                "Moving fast at location where walk leg and start of transit leg overlap, should produce on-board instruction",
                walkToBus20,
                new TraceData()
                    .withPosition(WALK_AND_TRANSIT_LEG_OVERLAP_POINT)
                    .withSpeed(8)
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction("Ride 4 min / 7 stops to Buford Hwy at Steve Dr (Accu-Car Expo)")
            ),
            Arguments.of(
                "Moving at location where walk leg and end of transit leg overlap, should produce instruction to get off",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(new Coordinates(33.960570, -84.004603))
                    .withSpeed(6)
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction("Get off at next stop (W Pike St & Old Norcross Rd)")
            ),
            Arguments.of(
                "Moving slowly at location where walk leg and end of transit leg overlap, should produce walk instruction",
                arrivingOnBus40,
                new TraceData()
                    .withPosition(new Coordinates(33.960583, -84.004595))
                    .withSpeed(1)
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withExpectedInstruction("Continue on crossing over service road")
            ),
            Arguments.of(
                "Deviated position because of gap between bus stop and path should direct to next walk leg and not state 'Upcoming/Arrived'.",
                walkFromBus40,
                new TraceData()
                    .withPosition(new Coordinates(33.9521485, -83.9927426))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction("Head to Langley Drive")
            )
        );
    }

    /**
     * Handle cases where user is in live tracking, and a saved itinerary becomes not monitorable
     * e.g. because real-time data is briefly lost from the agency, so OTP cannot find the desired itinerary.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canGenerateInstructionIfMatchingItineraryUndefined(boolean nullMatchingItinerary) throws Exception {
        assumeTrue(IS_END_TO_END);
        final int WEST_BEARING = 270;

        monitoredTrip = createMonitoredTrip(multiLegItinerary);
        monitoredTrip.journeyState.matchingItinerary = nullMatchingItinerary ? null : multiLegItinerary;
        monitoredTrip.journeyState.tripStatus = org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
        Persistence.monitoredTrips.replace(monitoredTrip.id, monitoredTrip);

        // Defaults to itinerary start time, unless specified otherwise.
        Leg multiItinFirstLeg = multiLegItinerary.legs.get(0);
        Coordinates multiItinFirstLegDestCoords = new Coordinates(multiItinFirstLeg.to);
        Instant instant = monitoredTrip.itinerary.startTime.toInstant();
        String jsonPayload = JsonUtils.toJson(
            createTrackPayload(
                monitoredTrip,
                createPoint(multiItinFirstLegDestCoords, 7, WEST_BEARING),
                0,
                // The timestamp has to be in seconds, hence the division by 1000.
                Date.from(Instant.ofEpochMilli(instant.toEpochMilli() / 1000))
            )
        );

        // Make a request to start a journey.
        var response = makeRequest(TRACK_TRIP_PATH, jsonPayload, headers, HttpMethod.POST);

        assertEquals(HttpStatus.OK_200, response.status);
        var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals("Unable to monitor trip.", trackResponse.instruction, "Live tracking is not possible if no matching itinerary.");
        assertEquals(TripStatus.NO_ITINERARY.name(), trackResponse.tripStatus);
    }

    @Test
    void canForciblyEndJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        monitoredTrip = createMonitoredTrip(itinerary);

        var startTrackingResponse = startTracking(createStartTrackingPayload(), HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        endTracking(
            FORCIBLY_END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createForceEndTrackingPayload(monitoredTrip.id))
        );

        // Check that the TrackedJourney Mongo record has been updated.
        TrackedJourney mongoTrackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(FORCIBLY_TERMINATED, mongoTrackedJourney.endCondition);
        assertNotEquals(-1, mongoTrackedJourney.longestConsecutiveDeviatedPoints);
    }
    
    @Test
    void canNotUseUnassociatedTrip() throws Exception {
        assumeTrue(IS_END_TO_END);
        var response = startTracking(
            createStartTrackingPayload("unassociated-trip-id"),
            HttpStatus.FORBIDDEN_403
        );
        assertEquals("Monitored trip is not associated with this user!", response.message);
    }

    @Test
    void canNotUpdateUnknownJourney() throws Exception {
        assumeTrue(IS_END_TO_END);
        var updateTrackingResponse = updateTracking(
            createUpdateTrackingPayload("unknown-journey-id"),
            HttpStatus.BAD_REQUEST_400
        );
        assertEquals("Provided journey does not exist or has already been completed!", updateTrackingResponse.message);
    }

    @Test
    void canNotUpdateCompletedJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var startTrackingResponse = startTracking(createStartTrackingPayload(), HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripStatus.DEVIATED.name(), startTrackingResponse.tripStatus);

        endTracking(startTrackingResponse.journeyId);

        var updateTrackingResponse = updateTracking(
            createUpdateTrackingPayload(startTrackingResponse.journeyId),
            HttpStatus.BAD_REQUEST_400
        );
        assertEquals("Provided journey does not exist or has already been completed!", updateTrackingResponse.message);
    }

    @ParameterizedTest
    @ValueSource(ints = {-300, 30, 3000})
    void canRerouteTrip(int offsetSeconds) throws Exception {
        assumeTrue(IS_END_TO_END);

        MonitoredTrip rerouteMonitoredTrip = monitoredTrip = createMonitoredTrip(walkToVoterRegCenterItinerary);
        rerouteMonitoredTrip.observers = soloOtpUser.relatedUsers;
        rerouteMonitoredTrip.leadTimeInMinutes = 10;
        Persistence.monitoredTrips.replace(rerouteMonitoredTrip.id, rerouteMonitoredTrip);

        var startTrackingPayload = new StartTrackingPayload();
        startTrackingPayload.tripId = rerouteMonitoredTrip.id;
        Step firstStep = walkToVoterRegCenterItinerary.legs.get(0).steps.get(0);
        startTrackingPayload.location = new TrackingLocation(Instant.now(), firstStep.lat, firstStep.lon);

        var mockOtpResponse = mockOtpReroutedPlanResponse();
        var expectedReroutedItinerary = getShortestDuration(mockOtpResponse.get().plan.itineraries);

        // Use current time relative to itinerary start (this will affect the computed target date after end tracking).
        DateTimeUtils.useFixedClockAt(
            ZonedDateTime.ofInstant(
                expectedReroutedItinerary.startTime.toInstant().plusSeconds(offsetSeconds),
                DateTimeUtils.getOtpZoneId()
            )
        );

        ManageTripTracking.otpResponseProviderOverride = mockOtpResponse;
        var deviatedPosition = new TrackingLocation(Instant.now(), 33.94412, -83.98899);
        var reroutingPoint = new Coordinates(expectedReroutedItinerary.legs.get(0).steps.get(2));
        var reroutingPointPosition = new TrackingLocation(Instant.now(), reroutingPoint.lat, reroutingPoint.lon);

        // Start tracking.
        var startTrackingResponse = startTracking(startTrackingPayload, HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        // Update tracking from a 'deviated' position.
        UpdatedTrackingPayload deviatedPositionPayload = createUpdateTrackingPayload(trackedJourney.id, List.of(deviatedPosition));
        var updateTrackingResponse = updateTracking(deviatedPositionPayload, HttpStatus.OK_200);
        // Confirm traveler is classed as 'deviated'.
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

        // Set up an itinerary provider that returns the original itinerary of the rerouted itinerary
        // based on the query param position.
        RerouteOtpResponseSupplier rerouteOtpResponseSupplier = new RerouteOtpResponseSupplier(
            deviatedPosition
        );

        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(
            tripAfterRerouting,
            rerouteOtpResponseSupplier::getOtpResponse
        );
        rerouteOtpResponseSupplier.setVariableSupplier(checkMonitoredTrip::getQueryParamsForTargetZonedDateTime);
        checkMonitoredTrip.run();
        Itinerary afterCheck = Persistence.monitoredTrips.getById(tripAfterRerouting.id).journeyState.matchingItinerary;
        assertEquals(beforeCheck.duration, afterCheck.duration);

        // Reroute again from a different location.
        trackedJourney.locations.clear();
        trackedJourney.locations.add(reroutingPointPosition);
        reroutedItinerary = ManageTripTracking.rerouteTrip(
            new TripTrackingData(tripAfterRerouting, trackedJourney, trackedJourney.locations)
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

        // Check that matching itinerary time corresponds to "today" if current time is before trip end,
        // or "tomorrow" if trip has ended (assuming a recurring trip).
        assertEquals(
            DateTimeUtils.nowAsZonedDateTime().plusDays(expectedReroutedItinerary.hasEnded() ? 1 : 0).toLocalDate(),
            ZonedDateTime.ofInstant(resetTrip.journeyState.matchingItinerary.startTime.toInstant(), DateTimeUtils.getOtpZoneId()).toLocalDate()
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

    private static long pollDepartedNotificationCount(MonitoredTrip rerouteMonitoredTrip) {
        return Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id).journeyState.lastNotifications
            .stream()
            .filter(n -> n.type == NotificationType.DEPARTED_NOTIFICATION)
            .count();
    }

    /** Provides a mock OTP 'plan' rerouted response. */
    public Supplier<OtpResponse> mockOtpReroutedPlanResponse() {
        return () -> {
            try {
                return OtpTestUtils.REROUTE_PLAN_RESPONSE.getResponse();
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

    private static List<TrackingLocation> createTrackingLocations() {
        return List.of(
            new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, getDateAndConvertToSeconds()),
            new TrackingLocation(90, 28.5398938204469, -81.3772773742676, 30, getDateAndConvertToSeconds()),
            new TrackingLocation(90, 29.5398938204469, -80.3772773742676, 31, getDateAndConvertToSeconds())
        );
    }

    private UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId) {
        return createUpdateTrackingPayload(journeyId, createTrackingLocations());
    }

    private UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId, List<TrackingLocation> locations) {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = locations;
        return payload;
    }

    private TrackPayload createTrackPayload(MonitoredTrip trip, List<TrackingLocation> locations) {
        var payload = new TrackPayload();
        payload.tripId = trip.id;
        payload.locations = locations;
        return payload;
    }

    private TrackPayload createTrackPayload(Coordinates coords) {
        return createTrackPayload(monitoredTrip, coords);
    }

    private TrackPayload createTrackPayload(MonitoredTrip trip, Coordinates coords) {
        return createTrackPayload(trip, coords, getDateAndConvertToSeconds());
    }

    private TrackPayload createTrackPayload(MonitoredTrip trip, Coordinates coords, Date date) {
        return createTrackPayload(trip, List.of(new TrackingLocation(date, coords.lat, coords.lon)));
    }

    private TrackPayload createTrackPayload(MonitoredTrip trip, Coordinates coords, int speed, Date date) {
        return createTrackPayload(trip, List.of(new TrackingLocation(0, coords.lat, coords.lon, speed, date)));
    }

    private EndTrackingPayload createEndTrackingPayload(String journeyId) {
        var payload = new EndTrackingPayload();
        payload.journeyId = journeyId;
        return payload;
    }

    private ForceEndTrackingPayload createForceEndTrackingPayload(String monitorTripId) {
        var payload = new ForceEndTrackingPayload();
        payload.tripId = monitorTripId;
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
    private class RerouteOtpResponseSupplier {
        private final TrackingLocation triggerLocation;
        private Supplier<OtpGraphQLVariables> variableSupplier;

        public RerouteOtpResponseSupplier(
            TrackingLocation triggerLocation
        ) {
            this.triggerLocation = triggerLocation;
        }

        public void setVariableSupplier(Supplier<OtpGraphQLVariables> supplier) {
            this.variableSupplier = supplier;
        }

        public OtpResponse getOtpResponse() {
            if (variableSupplier.get().fromPlace.endsWith(new Coordinates(triggerLocation).getCoordinates())) {
                return mockOtpReroutedPlanResponse().get();
            }
            OtpResponse response = new OtpResponse();
            response.plan = new TripPlan();
            response.plan.itineraries = List.of(walkToVoterRegCenterItinerary);
            return response;
        }
    }
}
