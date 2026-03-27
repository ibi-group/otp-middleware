package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;

import java.util.Date;

public class TrackedTripUtils {
    public static final String ROUTE_PATH = "api/secure/monitoredtrip/";
    public static final String START_TRACKING_TRIP_PATH = ROUTE_PATH + "starttracking";
    public static final String UPDATE_TRACKING_TRIP_PATH = ROUTE_PATH + "updatetracking";
    public static final String END_TRACKING_TRIP_PATH = ROUTE_PATH + "endtracking";
    public static final String FORCIBLY_END_TRACKING_TRIP_PATH = ROUTE_PATH + "forciblyendtracking";

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

}
