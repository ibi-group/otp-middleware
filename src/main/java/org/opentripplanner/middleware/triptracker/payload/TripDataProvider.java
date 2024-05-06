package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.List;

public class TripDataProvider {
    public String tripId;

    public String journeyId;

    public List<TrackingLocation> locations;

    private TripDataProvider(String tripId, String journeyId, List<TrackingLocation> locations) {
        this.tripId = tripId;
        this.journeyId = journeyId;
        this.locations = locations;
    }

    public static TripDataProvider from(StartTrackingPayload payload) {
        if (payload == null) return null;
        return new TripDataProvider(payload.tripId, null, List.of(payload.location));
    }

    public static TripDataProvider from(TrackPayload payload) {
        if (payload == null) return null;
        return new TripDataProvider(payload.tripId, null, payload.locations);
    }

    public static TripDataProvider from(UpdatedTrackingPayload payload) {
        if (payload == null) return null;
        return new TripDataProvider(null, payload.journeyId, payload.locations);
    }

    public static TripDataProvider from(EndTrackingPayload payload) {
        if (payload == null) return null;
        return new TripDataProvider(null, payload.journeyId, null);
    }

    public static TripDataProvider from(ForceEndTrackingPayload payload) {
        if (payload == null) return null;
        return new TripDataProvider(payload.tripId, null, null);
    }
}
