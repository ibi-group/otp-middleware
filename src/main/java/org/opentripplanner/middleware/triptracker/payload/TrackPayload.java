package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Trip tracking payload that covers the expect parameters for starting or updating trip tracking.
 */
public class TrackPayload implements TripDataProvider {

    private List<TrackingLocation> locations = new ArrayList<>();

    private String tripId;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public List<TrackingLocation> getLocations() {
        return locations;
    }
}
