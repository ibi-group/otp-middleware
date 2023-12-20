package org.opentripplanner.middleware.triptracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Trip tracking payload that covers the expect parameters for starting, updating and ending tracking.
 */
public class TrackingPayload {

    // Start tracking.
    public TrackingLocation location;

    // Start tracking.
    public String tripId;

    // Update and end tracking.
    public String journeyId;

    // Update tracking.
    public List<TrackingLocation> locations = new ArrayList<>();

    public TrackingPayload() {
    }
}
