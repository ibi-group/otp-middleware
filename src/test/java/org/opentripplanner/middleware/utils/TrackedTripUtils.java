package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;

public class TrackedTripUtils {

    private static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    private static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";

    /**
     * The mobile app sends timestamps in seconds which is then converted into milliseconds in {@link TripTrackingData}.
     * To represent this in testing, provide the time in seconds from epoch.
     */
    public static Date getDateAndConvertToSeconds() {
        return new Date(new Date().getTime() / 1000);
    }

    public static StartTrackingPayload createStartTrackingPayload(String monitorTripId) {
        var payload = new StartTrackingPayload();
        payload.tripId = monitorTripId;
        payload.location = new TrackingLocation(90, 24.1111111111111, -79.2222222222222, 29, getDateAndConvertToSeconds());
        return payload;
    }

    public static UpdatedTrackingPayload createUpdateTrackingPayload(String journeyId, List<TrackingLocation> locations) {
        var payload = new UpdatedTrackingPayload();
        payload.journeyId = journeyId;
        payload.locations = locations;
        return payload;
    }

    public static EndTrackingPayload createEndTrackingPayload(String journeyId) {
        var payload = new EndTrackingPayload();
        payload.journeyId = journeyId;
        return payload;
    }

    public static void endTracking(String path, String payload, Map<String, String> headers) throws JsonProcessingException {
        var response = makeRequest(path, payload, headers, HttpMethod.POST);
        var endTrackingResponse = JsonUtils.getPOJOFromJSON(response.responseBody, EndTrackingResponse.class);
        assertEquals(TripStatus.ENDED.name(), endTrackingResponse.tripStatus);
        assertEquals(HttpStatus.OK_200, response.status);
    }

    public static void endTracking(String journeyId, Map<String, String> headers) throws JsonProcessingException {
        endTracking(END_TRACKING_TRIP_PATH, JsonUtils.toJson(createEndTrackingPayload(journeyId)), headers);
    }
}
