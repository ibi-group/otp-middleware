package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.triptracker.TrackingLocation;

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
}
