package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.List;

public interface TripDataProvider {
    String getTripId();

    List<TrackingLocation> getLocations();
}
