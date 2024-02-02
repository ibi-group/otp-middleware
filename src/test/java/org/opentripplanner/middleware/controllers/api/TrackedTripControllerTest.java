package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripInstruction;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.StartTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.UpdateTrackingResponse;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;


public class TrackedTripControllerTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser soloOtpUser;
    private static MonitoredTrip monitoredTrip;
    private static TrackedJourney trackedJourney;

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    private static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    private static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";

    @BeforeAll
    public static void setUp() throws IOException {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);
        OtpTestUtils.mockOtpServer();
        soloOtpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));
        try {
            // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
            // from being able to use TEMP_AUTH0_USER_PASSWORD.
            var auth0User = Auth0Users.createAuth0UserForEmail(soloOtpUser.email, TEMP_AUTH0_USER_PASSWORD);
            soloOtpUser.auth0UserId = auth0User.getId();
            Persistence.otpUsers.replace(soloOtpUser.id, soloOtpUser);
        } catch (Auth0Exception e) {
            throw new RuntimeException(e);
        }
        monitoredTrip = new MonitoredTrip();
        monitoredTrip.userId = soloOtpUser.id;
        Persistence.monitoredTrips.create(monitoredTrip);
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
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripInstruction.GET_ON_BUS.name(), startTrackingResponse.instruction);
        assertEquals(TripStatus.NO_STATUS.name(), startTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload(startTrackingResponse.journeyId)),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, UpdateTrackingResponse.class);
        assertEquals(TripInstruction.STAY_ON_BUS.name(), updateTrackingResponse.instruction);
        assertEquals(TripStatus.NO_STATUS.name(), updateTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createEndTrackingPayload(startTrackingResponse.journeyId)),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripInstruction.NO_INSTRUCTION.name(), endTrackingResponse.instruction);
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
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        assertEquals("A journey of this trip has already been started. End the current journey before starting another.", startTrackingResponse.message);
        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
    }

    @Test
    void canForciblyEndJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            FORCIBLY_END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createForceEndTrackingPayload(monitoredTrip.id)),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripInstruction.NO_INSTRUCTION.name(), endTrackingResponse.instruction);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);
    }

    @Test
    void canNotUseUnassociatedTrip() throws Exception {
        assumeTrue(IS_END_TO_END);

        HttpResponseValues response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload("unassociated-trip-id")),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        assertEquals("Monitored trip is not associated with this user!", startTrackingResponse.message);
        assertEquals(HttpStatus.FORBIDDEN_403, response.status);
    }

    @Test
    void canNotUpdateUnknownJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        HttpResponseValues response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload("unknown-journey-id")),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, UpdateTrackingResponse.class);
        assertEquals("Provided journey does not exist or has already been completed!", updateTrackingResponse.message);
        assertEquals(HttpStatus.BAD_REQUEST_400, response.status);
    }

    @Test
    void canNotUpdateCompletedJourney() throws Exception {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            START_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createStartTrackingPayload()),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, StartTrackingResponse.class);
        trackedJourney = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        assertEquals(ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS, startTrackingResponse.frequencySeconds);
        assertEquals(TripInstruction.GET_ON_BUS.name(), startTrackingResponse.instruction);
        assertEquals(TripStatus.NO_STATUS.name(), startTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            END_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createEndTrackingPayload(startTrackingResponse.journeyId)),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );
        assertEquals(HttpStatus.OK_200, response.status);

        response = makeRequest(
            UPDATE_TRACKING_TRIP_PATH,
            JsonUtils.toJson(createUpdateTrackingPayload(startTrackingResponse.journeyId)),
            getMockHeaders(soloOtpUser),
            HttpMethod.POST
        );

        var updateTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, UpdateTrackingResponse.class);
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

    private UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId) {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = List.of(
            new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, new Date()),
            new TrackingLocation(90, 28.5398938204469, -81.3772773742676, 30, new Date()),
            new TrackingLocation(90, 29.5398938204469, -80.3772773742676, 31, new Date())
        );
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
