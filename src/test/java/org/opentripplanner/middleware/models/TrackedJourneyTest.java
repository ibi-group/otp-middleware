package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackedJourneyTest {
    @Test
    void canComputeTotalDeviation() {
        TrackedJourney journey = new TrackedJourney();
        journey.locations = null;
        assertEquals(-1.0, journey.computeTotalDeviation());

        journey.locations = Stream
            .of(11.0, 23.0, 6.4)
            .map(d -> {
                TrackingLocation location = new TrackingLocation();
                location.deviationMeters = d;
                return location;
            })
            .collect(Collectors.toList());
        assertEquals(40.4, journey.computeTotalDeviation());
    }

    @Test
    void canComputeLargestConsecutiveDeviations() {
        TrackedJourney journey = new TrackedJourney();
        journey.locations = null;
        assertEquals(-1, journey.computeLargestConsecutiveDeviations());

        journey.locations = Stream
            .of(0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0)
            .map(d -> {
                TrackingLocation location = new TrackingLocation();
                location.tripStatus = d == 1 ? TripStatus.DEVIATED : TripStatus.ON_SCHEDULE;
                location.speed = 1;
                return location;
            })
            .collect(Collectors.toList());

        // Insert a location where the traveler is not moving.
        journey.locations.get(4).speed = 0;

        assertEquals(4, journey.computeLargestConsecutiveDeviations());
    }
}
