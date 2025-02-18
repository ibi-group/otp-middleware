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
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
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
import static org.opentripplanner.middleware.triptracker.ManageTripTracking.setOtpGraphQLVariables;
import static org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction.TRIP_INSTRUCTION_END_OF_ROUTING;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

public class TrackedTripControllerTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser soloOtpUser;
    private static TrackedJourney trackedJourney;
    private static Itinerary itinerary;
    private static Itinerary multiLegItinerary;
    private static Itinerary walkToVoterRegCenterItinerary;
    private static Itinerary destinationAwayFromSidewalk;

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    private static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    private static final String TRACK_TRIP_PATH = ROUTE_PATH + "track";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    private static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";
    private static HashMap<String, String> headers;

    private MonitoredTrip monitoredTrip;

    @BeforeAll
    public static void setUp() throws Exception {
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
        destinationAwayFromSidewalk = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/destination-away-from-sidewalk.json"),
            Itinerary.class
        );

        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
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
        trip.tripTime = DateTimeUtils.convertToLocalDateTime(itin.startTime).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        trip.journeyState = new JourneyState();
        trip.journeyState.matchingItinerary = itin;
        // Original target date should be populated but does not really matter.
        trip.journeyState.targetDate = "2024-01-26";
        Persistence.monitoredTrips.create(trip);
        return trip;
    }

    @AfterAll
    public static void tearDown() throws Exception {
        assumeTrue(IS_END_TO_END);
        restoreDefaultAuthDisabled();
        soloOtpUser = Persistence.otpUsers.getById(soloOtpUser.id);
        if (soloOtpUser != null) soloOtpUser.delete(true);
    }

    @BeforeEach
    public void beforeEachTest() {
        assumeTrue(IS_END_TO_END);
        monitoredTrip = createMonitoredTrip(itinerary);
    }

    @AfterEach
    public void tearDownAfterTest() {
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
        Itinerary itin,
        Coordinates coords,
        String instruction,
        TripStatus status,
        String message
    ) throws Exception {
        assumeTrue(IS_END_TO_END);

        monitoredTrip = createMonitoredTrip(itin);

        String jsonPayload = JsonUtils.toJson(
            createTrackPayload(monitoredTrip, coords, Date.from(Instant.ofEpochMilli(monitoredTrip.itinerary.startTime.getTime() / 1000)))
        );

        // Make a request to start a journey.
        var response = makeRequest(TRACK_TRIP_PATH, jsonPayload, headers, HttpMethod.POST);

        assertEquals(HttpStatus.OK_200, response.status);
        var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals(instruction, trackResponse.instruction, message);
        assertEquals(status.name(), trackResponse.tripStatus);
        assertNotNull(trackResponse.journeyId);
        trackedJourney = Persistence.trackedJourneys.getById(trackResponse.journeyId);

        // Check that deviation fields get computed and recorded.
        Double deviationMeters = trackedJourney.lastLocation().deviationMeters;
        assertNotNull(deviationMeters);
        assertNotEquals(0, deviationMeters);

        // Second request to update a journey
        response = makeRequest(
            TRACK_TRIP_PATH,
            jsonPayload,
            headers,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertNotEquals(0, trackResponse.frequencySeconds);
        assertEquals(instruction, trackResponse.instruction, message);
        assertNotNull(trackResponse.journeyId);
        assertEquals(trackedJourney.id, trackResponse.journeyId);
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

        return Stream.of(
            Arguments.of(
                itinerary,
                createPoint(firstStepCoords, 1, NORTH_EAST_BEARING),
                new OnTrackInstruction(1, adairAvenueNortheastStep, locale).build(),
                TripStatus.ON_SCHEDULE,
                "Coords near first step should produce relevant instruction"
            ),
            Arguments.of(
                itinerary,
                createPoint(firstStepCoords, 4, NORTH_EAST_BEARING),
                new OnTrackInstruction(4, adairAvenueNortheastStep, locale).build(),
                TripStatus.ON_SCHEDULE,
                "Coords in the 'upcoming' range of first step should produce relevant instruction and deemed not deviated."
            ),
            Arguments.of(
                itinerary,
                createPoint(firstStepCoords, 30, NORTH_EAST_BEARING),
                new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale).build(),
                TripStatus.DEVIATED,
                "Deviated coords near first step should produce instruction to head to first step #1"
            ),
            Arguments.of(
                itinerary,
                createPoint(firstStepCoords, 15, NORTH_WEST_BEARING),
                new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale).build(),
                TripStatus.DEVIATED,
                "Deviated coords near first step should produce instruction to head to first step #2"
            ),
            Arguments.of(
                itinerary,
                createPoint(firstStepCoords, 20, WEST_BEARING),
                new ContinueInstruction(adairAvenueNortheastStep, locale).build(),
                TripStatus.ON_SCHEDULE,
                "Coords along a step should produce a continue on street instruction"
            ),
            Arguments.of(
                itinerary,
                thirdStepCoords,
                new OnTrackInstruction(0, ponceDeLeonPlaceNortheastStep, locale).build(),
                TripStatus.AHEAD_OF_SCHEDULE,
                "Coords near a not-first step should produce relevant instruction"
            ),
            Arguments.of(
                itinerary,
                createPoint(thirdStepCoords, 30, NORTH_WEST_BEARING),
                new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale).build(),
                TripStatus.DEVIATED,
                "Deviated coords near a not-first step should produce instruction to head to step"
            ),
            Arguments.of(
                itinerary,
                createPoint(destinationCoords, 1, NORTH_WEST_BEARING),
                TRIP_INSTRUCTION_END_OF_ROUTING, // Fixme new OnTrackInstruction(2, monroeDrDestinationName, locale).build(),
                TripStatus.COMPLETED,
                "Instructions for destination coordinate"
            ),
            Arguments.of(
                multiLegItinerary,
                createPoint(multiItinFirstLegDestCoords, 1.5, WEST_BEARING),
                new WaitForTransitInstruction(
                    multiItinBusLeg,
                    multiItinBusLeg.getScheduledStartTime().toInstant().minus(Duration.ofMinutes(6)),
                    locale)
                    .build(),
                TripStatus.AHEAD_OF_SCHEDULE,
                "Arriving ahead of schedule to a bus stop at the end of first leg."
            ),
            // This position overlaps with the beginning of the transit trip,
            // but it is still within the 'upcoming' radius of the stop, so display a "wait for transit" instruction.
            Arguments.of(
                multiLegItinerary,
                createPoint(multiItinFirstLegDestCoords, 1.5, NORTH_EAST_BEARING),
                new WaitForTransitInstruction(
                    multiItinBusLeg,
                    multiItinBusLeg.getScheduledStartTime().toInstant().minus(Duration.ofMinutes(6)),
                    locale)
                    .build(),
                TripStatus.AHEAD_OF_SCHEDULE,
                "Arriving ahead of schedule to a bus stop at the end of first leg should produce a non-trivial instruction."
            ),
            Arguments.of(
                multiLegItinerary,
                createPoint(multiItinFirstLegDestCoords, 7, WEST_BEARING),
                new WaitForTransitInstruction(
                    multiItinBusLeg,
                    multiItinBusLeg.getScheduledStartTime().toInstant().minus(Duration.ofMinutes(6)),
                    locale)
                    .build(),
                TripStatus.AHEAD_OF_SCHEDULE,
                "Arriving ahead of schedule near a bus stop (in 'upcoming' range) at the end of first leg."
            ),
            Arguments.of(
                multiLegItinerary,
                createPoint(multiItinLastLegDestCoords, 1, NORTH_WEST_BEARING),
                new OnTrackInstruction(1, ansleyMallPetShopDestinationName, locale).build(),
                TripStatus.COMPLETED,
                "Instructions for destination coordinate of multi-leg trip"
            ),
            Arguments.of(
                destinationAwayFromSidewalk,
                pointNearEndOfSidewalk,
               TRIP_INSTRUCTION_END_OF_ROUTING, // TODO: improve this with "in vicinity"
                TripStatus.COMPLETED,
                "Arrival instruction when destination is away from sidewalk"
            ),
            Arguments.of(
                destinationAwayFromSidewalk,
                pointPastEndOfSidewalk,
                TRIP_INSTRUCTION_END_OF_ROUTING, // TODO: improve this with "in vicinity"
                TripStatus.COMPLETED,
                "Arrival instruction when destination is away from sidewalk"
            ),
            Arguments.of(
                itinerary,
                createPoint(thirdStepCoords, 1000, NORTH_WEST_BEARING),
                NO_INSTRUCTION,
                TripStatus.DEVIATED,
                "Deviated significantly from nearest step should produce no instruction"
            )
        );
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

    @Test
    void canRerouteTrip() throws Exception {
        assumeTrue(IS_END_TO_END);

        MonitoredTrip rerouteMonitoredTrip = monitoredTrip = createMonitoredTrip(walkToVoterRegCenterItinerary);

        var startTrackingPayload = createStartTrackingPayload(rerouteMonitoredTrip.id);
        var mockOtpResponse = mockOtpReroutedPlanResponse();
        var expectedReroutedItinerary = getShortestDuration(mockOtpResponse.get().plan.itineraries);
        ManageTripTracking.otpResponseProviderOverride = mockOtpResponse;
        var deviatedPosition = new TrackingLocation(Instant.now(), 33.94412, -83.98899);
        var reroutingPoint = new Coordinates(expectedReroutedItinerary.legs.get(0).steps.get(2));
        var reroutingPointPosition = new TrackingLocation(Instant.now(), reroutingPoint.lat,reroutingPoint.lon);

        // Start tracking.
        var startTrackingResponse = startTracking(startTrackingPayload, HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        // Update tracking from a 'deviated' position.
        UpdatedTrackingPayload deviatedPositionPayload = createUpdateTrackingPayload(trackedJourney.id, List.of(deviatedPosition));
        var updateTrackingResponse = updateTracking(deviatedPositionPayload, HttpStatus.OK_200);
        // Confirm traveler is classed as 'deviated'.
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);

        // Reroute trip from 'deviated' position.
        var reroutedItinerary = ManageTripTracking.rerouteTrip(
            new TripTrackingData(rerouteMonitoredTrip, trackedJourney, List.of(deviatedPosition))
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

        rerouteMonitoredTrip.tripTime = "12:31";
        Itinerary beforeCheck = rerouteMonitoredTrip.journeyState.matchingItinerary;
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(rerouteMonitoredTrip);
        checkMonitoredTrip.run();
        Itinerary afterCheck = Persistence.monitoredTrips.getById(rerouteMonitoredTrip.id).journeyState.matchingItinerary;
        assertEquals(beforeCheck.duration, afterCheck.duration);

        // Reroute again from a different location.
        trackedJourney.locations.clear();
        trackedJourney.locations.add(reroutingPointPosition);
        reroutedItinerary = ManageTripTracking.rerouteTrip(
            new TripTrackingData(rerouteMonitoredTrip, trackedJourney, trackedJourney.locations)
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

        // Check that matching itinerary time corresponds to "today".
        assertEquals(
            DateTimeUtils.nowAsZonedDateTime(DateTimeUtils.getOtpZoneId()).toLocalDate(),
            ZonedDateTime.ofInstant(resetTrip.journeyState.matchingItinerary.startTime.toInstant(), DateTimeUtils.getOtpZoneId()).toLocalDate()
        );

        // Start tracking again from the same last position above. Traveler should be deviated.
        startTrackingResponse = startTracking(startTrackingPayload, HttpStatus.OK_200);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);

        updateTrackingResponse = updateTracking(
            createUpdateTrackingPayload(trackedJourney.id, List.of(reroutingPointPosition)),
            HttpStatus.OK_200
        );
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);
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
}
