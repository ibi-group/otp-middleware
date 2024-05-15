package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.TrackPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

public class TrackedTripControllerTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser soloOtpUser;
    private static MonitoredTrip monitoredTrip;
    private static TrackedJourney trackedJourney;
    private static Itinerary itinerary;

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    private static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    private static final String TRACK_TRIP_PATH = ROUTE_PATH + "track";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    private static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";
    private static HashMap<String, String> headers;

    @BeforeAll
    public static void setUp() throws Exception {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);
        OtpTestUtils.mockOtpServer();

        itinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/adair-avenue-to-monroe-drive.json"),
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
        monitoredTrip = new MonitoredTrip();
        monitoredTrip.userId = soloOtpUser.id;
        monitoredTrip.itinerary = itinerary;
        monitoredTrip.journeyState = new JourneyState();
        monitoredTrip.journeyState.matchingItinerary = itinerary;
        Persistence.monitoredTrips.create(monitoredTrip);
        headers = getMockHeaders(soloOtpUser);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        assumeTrue(IS_END_TO_END);
        restoreDefaultAuthDisabled();
        soloOtpUser = Persistence.otpUsers.getById(soloOtpUser.id);
        if (soloOtpUser != null) soloOtpUser.delete(true);
        monitoredTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        if (monitoredTrip != null) monitoredTrip.delete();
    }

    @AfterEach
    public void tearDownAfterTest() {
        if (trackedJourney != null) {
            trackedJourney.delete();
            trackedJourney = null;
        }
    }

    @Test
    void canCompleteJourneyLifeCycle() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            headers,
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripStatus.DEVIATED.name(), startTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        // A single location is submitted when starting tracking.
        assertEquals(1, trackedJourney.locations.size());
        assertEquals(TripStatus.DEVIATED, trackedJourney.lastLocation().tripStatus);

        response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload(startTrackingResponse.journeyId)),
            headers,
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals(TripStatus.DEVIATED.name(), updateTrackingResponse.tripStatus);
        assertNotEquals(0, updateTrackingResponse.frequencySeconds);
        assertNotNull(updateTrackingResponse.journeyId);
        assertEquals(HttpStatus.OK_200, response.status);

        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        // The call to updatetracking sent 3 additional locations, so there are 4 locations stored at this point.
        assertEquals(4, trackedJourney.locations.size());
        assertEquals(trackedJourney.locations.get(3), trackedJourney.lastLocation());
        assertEquals(TripStatus.DEVIATED, trackedJourney.lastLocation().tripStatus);

        response = makeRequest(
            END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createEndTrackingPayload(startTrackingResponse.journeyId)),
            headers,
            HttpMethod.POST
        );
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    @Test
    void canNotRestartAnOngoingJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            headers,
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            headers,
            HttpMethod.POST
        );

        startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals("A journey of this trip has already been started. End the current journey before starting another.", startTrackingResponse.message);
        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
    }

    @ParameterizedTest
    @MethodSource("createStartThenUpdateCases")
    void canStartThenUpdateOngoingJourney(Coordinates coords, String instruction, String message) throws Exception {
        assumeTrue(IS_END_TO_END);

        // First request for starting a journey.
        var response = makeRequest(
            TRACK_TRIP_PATH,
            JsonUtils.toJson(createTrackPayload(List.of(new TrackingLocation(Instant.now(), coords.lat, coords.lon)))),
            headers,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertNotEquals(0, trackResponse.frequencySeconds);
        assertEquals(instruction, trackResponse.instruction, message);
        assertNotNull(trackResponse.journeyId);
        trackedJourney = Persistence.trackedJourneys.getById(trackResponse.journeyId);

        // Second request to update a journey
        response = makeRequest(
            TRACK_TRIP_PATH,
            JsonUtils.toJson(createTrackPayload(List.of(new TrackingLocation(Instant.now(), coords.lat, coords.lon)))),
            headers,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals(instruction, trackResponse.instruction, message);
        assertNotNull(trackResponse.journeyId);
        assertEquals(trackedJourney.id, trackResponse.journeyId);
    }

    private static Stream<Arguments> createStartThenUpdateCases() {
        final int NORTH_WEST_BEARING = 315;
        final int NORTH_EAST_BEARING = 45;
        final int WEST_BEARING = 270;

        Leg firstLeg = itinerary.legs.get(0);
        Coordinates firstStepCoords = new Coordinates(firstLeg.steps.get(0));
        Coordinates thirdStepCoords = new Coordinates(firstLeg.steps.get(2));

        return Stream.of(
            Arguments.of(
                createPoint(firstStepCoords, 1, NORTH_EAST_BEARING),
                "IMMEDIATE: Head WEST on Adair Avenue Northeast",
                "Coords near first step should produce relevant instruction"
            ),
            Arguments.of(
                createPoint(firstStepCoords, 4, NORTH_EAST_BEARING),
                "UPCOMING: Head WEST on Adair Avenue Northeast",
                "Coords near first step should produce relevant instruction"
            ),
            Arguments.of(
                createPoint(firstStepCoords, 30, NORTH_EAST_BEARING),
                "Head to Adair Avenue Northeast",
                "Deviated coords near first step should produce instruction to head to first step #1"
            ),
            Arguments.of(
                createPoint(firstStepCoords, 15, NORTH_WEST_BEARING),
                "Head to Adair Avenue Northeast",
                "Deviated coords near first step should produce instruction to head to first step #2"
            ),
            Arguments.of(
                createPoint(firstStepCoords, 20, WEST_BEARING),
                NO_INSTRUCTION,
                "Coords along a step should produce no instruction"
            ),
            Arguments.of(
                thirdStepCoords,
                "IMMEDIATE: LEFT on Ponce de Leon Place Northeast",
                "Coords near a not-first step should produce relevant instruction"
            ),
            Arguments.of(
                createPoint(thirdStepCoords, 30, NORTH_WEST_BEARING),
                "Head to Ponce de Leon Place Northeast",
                "Deviated coords near a not-first step should produce instruction to head to step"
            )
        );
    }

    //@Test
    void canStartThenUpdateOngoingJourney1() throws Exception {
        assumeTrue(IS_END_TO_END);

        final String FIRST_INSTRUCTION_IF_DEVIATED = "Head to Adair Avenue Northeast";
        final int NORTH_WEST_BEARING = 315;

        Leg firstLeg = itinerary.legs.get(0);
        Step thirdStep = firstLeg.steps.get(2);
        Coordinates thirdStepCoords = new Coordinates(thirdStep);
        Coordinates deviatedCoordsNearThirdStep = createPoint(thirdStepCoords, 30, NORTH_WEST_BEARING);

        // First request deviated should keep same instruction.
        var response = makeRequest(
            TRACK_TRIP_PATH,
            // JsonUtils.toJson(createTrackPayload()),
            JsonUtils.toJson(createTrackPayload(List.of(new TrackingLocation(Instant.now(), deviatedCoordsNearThirdStep.lat, deviatedCoordsNearThirdStep.lon)))),
            headers,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        var trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertNotEquals(0, trackResponse.frequencySeconds);
        // When starting tracking, a deviated location should result in an instruction to head to the first step.
        assertEquals(FIRST_INSTRUCTION_IF_DEVIATED, trackResponse.instruction);
        assertNotNull(trackResponse.journeyId);
        trackedJourney = Persistence.trackedJourneys.getById(trackResponse.journeyId);

        // Test 1
        response = makeRequest(
            TRACK_TRIP_PATH,
            JsonUtils.toJson(createTrackPayload(List.of(new TrackingLocation(Instant.now(), deviatedCoordsNearThirdStep.lat, deviatedCoordsNearThirdStep.lon)))),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        // When updating tracking, a deviated location near a step that is not the first step
        // should result in an instruction to head to that closest step (and not the first step).
        assertNotEquals("Head to Ponce de Leon Place Northeast", trackResponse.instruction);
        assertNotNull(trackResponse.journeyId);
        assertEquals(trackedJourney.id, trackResponse.journeyId);

        // Test 2
        response = makeRequest(
            TRACK_TRIP_PATH,
            JsonUtils.toJson(createTrackPayload()),
            headers,
            HttpMethod.POST
        );

        assertEquals(HttpStatus.OK_200, response.status);
        trackResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        // When updating tracking, a deviated location near a step that is not the first step
        // should result in an instruction to head to that closest step (and not the first step).
        assertNotEquals(FIRST_INSTRUCTION_IF_DEVIATED, trackResponse.instruction);
        assertNotNull(trackResponse.journeyId);
        assertEquals(trackedJourney.id, trackResponse.journeyId);
    }

    @Test
    void canForciblyEndJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            headers,
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            FORCIBLY_END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createForceEndTrackingPayload(monitoredTrip.id)),
            headers,
            HttpMethod.POST
        );
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);
    }

    @Test
    void canNotUseUnassociatedTrip() throws Exception {
        assumeTrue(IS_END_TO_END);

        HttpResponseValues response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload("unassociated-trip-id")),
            headers,
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals("Monitored trip is not associated with this user!", startTrackingResponse.message);
        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
    }

    @Test
    void canNotUpdateUnknownJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        HttpResponseValues response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload("unknown-journey-id")),
            headers,
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals("Provided journey does not exist or has already been completed!", updateTrackingResponse.message);
        assertEquals(HttpStatus.BAD_REQUEST_400, response.status);
    }

    @Test
    void canNotUpdateCompletedJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            headers,
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripStatus.DEVIATED.name(), startTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createEndTrackingPayload(startTrackingResponse.journeyId)),
            headers,
            HttpMethod.POST
        );
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload(startTrackingResponse.journeyId)),
            headers,
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        assertEquals("Provided journey does not exist or has already been completed!", updateTrackingResponse.message);
        assertEquals(HttpStatus.BAD_REQUEST_400, response.status);
    }


    private StartTrackingPayload createStartTrackingPayload() {
        return createStartTrackingPayload(monitoredTrip.id);
    }

    private StartTrackingPayload createStartTrackingPayload(String monitorTripId) {
        return createStartTrackingPayload(monitorTripId, 24.1111111111111, -79.2222222222222, new Date().getTime());
    }

    private StartTrackingPayload createStartTrackingPayload(String monitorTripId, double lat, double lon, long timestamp) {
        var payload = new StartTrackingPayload();
        payload.tripId = monitorTripId;
        payload.location = new TrackingLocation(90, lat, lon, 29, new Date(timestamp));
        return payload;
    }

    private static List<TrackingLocation> createTrackingLocations() {
        return List.of(
            new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, new Date()),
            new TrackingLocation(90, 28.5398938204469, -81.3772773742676, 30, new Date()),
            new TrackingLocation(90, 29.5398938204469, -80.3772773742676, 31, new Date())
        );
    }

    private UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId) {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = createTrackingLocations();
        return payload;
    }

    private TrackPayload createTrackPayload() {
        return createTrackPayload(createTrackingLocations());
    }

    private TrackPayload createTrackPayload(List<TrackingLocation> locations) {
        var payload = new TrackPayload();
        payload.tripId = monitoredTrip.id;
        payload.locations = locations;
        return payload;
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
}
