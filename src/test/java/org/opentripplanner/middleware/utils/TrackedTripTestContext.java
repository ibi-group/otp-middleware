package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.TrackPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;

/**
 * Holds ambient test data such as OTP users and related data.
 */
public class TrackedTripTestContext {
    public static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    public static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    public static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    public static final String TRACK_TRIP_PATH = ROUTE_PATH + "track";
    public static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    public static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";

    public final OtpUser otpUser;
    public final Map<String, String> headers;
    private final Set<TrackedJourney> createdJourneys = new HashSet<>();
    private final List<MonitoredTrip> createdTrips = new ArrayList<>();

    public TrackedTripTestContext() throws Exception {
        otpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));

        // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
        // from being able to use TEMP_AUTH0_USER_PASSWORD.
        var auth0User = Auth0Users.createAuth0UserForEmail(otpUser.email, TEMP_AUTH0_USER_PASSWORD);
        otpUser.auth0UserId = auth0User.getId();
        Persistence.otpUsers.replace(otpUser.id, otpUser);
        headers = ApiTestUtils.getMockHeaders(otpUser);
    }

    public void tearDown() {
        OtpUser fetchedUser = Persistence.otpUsers.getById(otpUser.id);
        if (fetchedUser != null) fetchedUser.delete(true);
    }

    public void cleanUpAfterTest() {
        createdJourneys.forEach(j -> {
            j = Persistence.trackedJourneys.getById(j.id);
            if (j != null) j.delete();
        });
        createdJourneys.clear();

        createdTrips.forEach(t -> {
            t = Persistence.monitoredTrips.getById(t.id);
            if (t != null) t.delete();
        });
        createdTrips.clear();
    }

    public TrackingResponse startTracking(String tripId, int expectedStatus) throws JsonProcessingException {
        return startTracking(tripId, makeDefaultLocation(), expectedStatus);
    }

    public static TrackingLocation makeDefaultLocation() {
        return new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, DateTimeUtils.dateAsSeconds());
    }

    public TrackingResponse startTracking(String tripId, TrackingLocation location, int expectedStatus) throws JsonProcessingException {
        var payload = new StartTrackingPayload();
        payload.tripId = tripId;
        payload.location = location;
        var response = makeRequest(START_TRACKING_TRIP_PATH, JsonUtils.toJson(payload), headers, HttpMethod.POST);
        assertEquals(expectedStatus, response.status);

        var startTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        TrackedJourney journey = Persistence.trackedJourneys.getById(startTrackingResponse.journeyId);
        if (journey != null) createdJourneys.add(journey);
        return startTrackingResponse;
    }

    public TrackingResponse track(String tripId, Coordinates coords, int expectedStatus) throws JsonProcessingException {
        return track(tripId, coords, 0, Instant.now(), expectedStatus);
    }

    public TrackingResponse track(String tripId, Coordinates coords, int speed, Instant instant, int expectedStatus) throws JsonProcessingException {
        // The date stored in tracking location has to be from a timestamp expressed in seconds.
        Date dateAsSeconds = new Date(instant.getEpochSecond());
        var payload = new TrackPayload();
        payload.tripId = tripId;
        payload.locations = List.of(new TrackingLocation(0, coords.lat, coords.lon, speed, dateAsSeconds));

        var response = makeRequest(TRACK_TRIP_PATH, JsonUtils.toJson(payload), headers, HttpMethod.POST);
        assertEquals(expectedStatus, response.status);

        var trackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
        TrackedJourney journey = Persistence.trackedJourneys.getById(trackingResponse.journeyId);
        if (journey != null) createdJourneys.add(journey);
        return trackingResponse;
    }

    public TrackingResponse updateTracking(String journeyId, List<TrackingLocation> locations, int expectedStatus) throws JsonProcessingException {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = locations;
        var response = makeRequest(UPDATE_TRACKING_TRIP_PATH, JsonUtils.toJson(payload), headers, HttpMethod.POST);
        assertEquals(expectedStatus, response.status);
        return JsonUtils.getPOJOFromJSON(response.responseBody, TrackingResponse.class);
    }

    private void endTracking(String path, String payload) throws JsonProcessingException {
        var response = makeRequest(path, payload, headers, HttpMethod.POST);
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);
    }

    public void endTracking(String journeyId) throws JsonProcessingException {
        var payload = new EndTrackingPayload();
        payload.journeyId = journeyId;
        endTracking(END_TRACKING_TRIP_PATH, JsonUtils.toJson(payload));
    }

    public void endTracking(MonitoredTrip trip) throws JsonProcessingException {
        var payload = new ForceEndTrackingPayload();
        payload.tripId = trip.id;
        endTracking(FORCIBLY_END_TRACKING_TRIP_PATH, JsonUtils.toJson(payload));
    }

    public MonitoredTrip createMonitoredTrip(Itinerary itin) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = otpUser.id;
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
        createdTrips.add(trip);
        return trip;
    }

    public void registerJourney(TrackedJourney journey) {
        Persistence.trackedJourneys.create(journey);
        createdJourneys.add(journey);
    }
}
