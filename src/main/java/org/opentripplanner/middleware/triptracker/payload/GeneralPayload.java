package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.List;

/**
 * Holds data from different payload types into a common shape.
 */
public class GeneralPayload {
    public String tripId;

    public String journeyId;

    public List<TrackingLocation> locations;

    public TrackingLocation location;

    public GeneralPayload() {
        // Used by serialization.
    }

    public List<TrackingLocation> getLocations() {
        return location != null ? List.of(location) : locations;
    }
}
