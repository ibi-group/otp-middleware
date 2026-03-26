package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;

import java.util.Date;
import java.util.List;

public class TrackedTripUtils {

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
}
