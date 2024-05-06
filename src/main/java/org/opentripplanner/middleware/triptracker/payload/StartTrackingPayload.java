package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.List;

/**
 * Trip tracking payload that covers the expect parameters for starting a tracked trip.
 */
public class StartTrackingPayload implements TripDataProvider {

    public TrackingLocation location;

    private String tripId;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public List<TrackingLocation> getLocations() {
        return List.of(location);
    }

    public StartTrackingPayload() {
        // Used for serialization.
    }
}
