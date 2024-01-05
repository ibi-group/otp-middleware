package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

/**
 * Trip tracking payload that covers the expect parameters for starting a tracked trip.
 */
public class StartTrackingPayload {

    public TrackingLocation location;

    public String tripId;

}
