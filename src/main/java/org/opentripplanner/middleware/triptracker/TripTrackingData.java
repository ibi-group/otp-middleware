package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;

import java.util.List;

/**
 * Helper class that holds a {@link MonitoredTrip}, {@link TrackedJourney}, and a list of {@link TrackingLocation}
 * involved with the trip tracking endpoints.
 */
public class TripTrackingData {
    public final MonitoredTrip trip;
    public final TrackedJourney journey;
    public final List<TrackingLocation> locations;

    public TripTrackingData(MonitoredTrip trip, TrackedJourney journey, List<TrackingLocation> locations) {
        this.trip = trip;
        this.journey = journey;
        this.locations = locations;
    }
}
