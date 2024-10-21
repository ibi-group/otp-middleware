package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackedJourneyTest {
    @Test
    void canComputeLargestConsecutiveDeviations() {
        TrackedJourney journey = new TrackedJourney();
        journey.locations = null;
        assertEquals(-1, journey.computeLargestConsecutiveDeviations());

        journey.locations = Stream
            .of(0, 1, 1, 1, null, 1, 1, 0, 0, null, 1, 1, 1, null, 0)
            .map(d -> {
                TrackingLocation location = new TrackingLocation();
                location.tripStatus = d == null
                    ? null
                    : d == 1
                    ? TripStatus.DEVIATED
                    : TripStatus.ON_SCHEDULE;
                location.speed = 1;
                return location;
            })
            .collect(Collectors.toList());

        // Insert a location where the traveler is not moving.
        journey.locations.get(5).speed = 0;

        // After excluding the nulls, count the first group of consecutive ones.
        assertEquals(4, journey.computeLargestConsecutiveDeviations());
    }
}
