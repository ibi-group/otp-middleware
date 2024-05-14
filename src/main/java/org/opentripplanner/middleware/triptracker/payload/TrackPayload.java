package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Trip tracking payload that covers the expect parameters for starting or updating trip tracking.
 */
public class TrackPayload {

    public List<TrackingLocation> locations = new ArrayList<>();

    public String tripId;

}
