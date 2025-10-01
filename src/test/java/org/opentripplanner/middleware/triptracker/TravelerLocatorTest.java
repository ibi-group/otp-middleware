package org.opentripplanner.middleware.triptracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.stopsUntilEndOfLeg;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

class TravelerLocatorTest {
    @Test
    void testStopsUntilEndOfLeg() {
        Leg leg = new Leg();
        leg.to = createPlace("FinalStop");
        leg.intermediateStops = List.of(
            createPlace("Stop0"),
            createPlace("Stop1"),
            createPlace("Stop2"),
            createPlace("Stop3"),
            createPlace("Stop4"),
            createPlace("Stop5"),
            createPlace("Stop6")
        );

        for (int i = 0; i < leg.intermediateStops.size(); i++) {
            Place stop = leg.intermediateStops.get(i);
            assertEquals(7 - i, stopsUntilEndOfLeg(stop, leg), stop.stopId);
        }
        assertEquals(0, stopsUntilEndOfLeg(leg.to, leg), leg.to.stopId);
    }

    Place createPlace(String id) {
        Place place = new Place();
        place.stopId = id;
        return place;
    }

    @ParameterizedTest
    @MethodSource("createNearTransitLegOriginCases")
    void testIsNearTransitLegOrigin(Coordinates stopCoordinates, Coordinates userCoordinates, boolean expectedLeg) {
        Leg transitLeg = new Leg();
        // Populating fields to avoid null pointer exceptions, but only 'transitLeg' and 'from' matter.
        transitLeg.transitLeg = true;
        transitLeg.duration = 60.0;
        transitLeg.from = createPlace("TransitLegOrigin");
        transitLeg.from.lat = stopCoordinates.lat;
        transitLeg.from.lon = stopCoordinates.lon;

        // Other arbitrary transit leg with arbitrary 'from' location far from the above leg.
        // This leg needs to be considered if traveler is on an itinerary with two transit legs back to back.
        Leg otherTransitLeg = new Leg();
        // Populating fields to avoid null pointer exceptions, but only 'transitLeg' and 'from' matter.
        otherTransitLeg.transitLeg = true;
        otherTransitLeg.duration = 60.0;
        otherTransitLeg.from = createPlace("OtherTransitLegOrigin");
        otherTransitLeg.from.lat = stopCoordinates.lat + 0.5;
        otherTransitLeg.from.lon = stopCoordinates.lon - 0.5;

        TravelerPosition position = new TravelerPosition.Builder()
            .setExpectedLeg(transitLeg)
            .setNextLeg(otherTransitLeg)
            // Current time is needed to avoid null pointer exceptions
            .setCurrentTime(Instant.now())
            .setCurrentPosition(userCoordinates)
            .build();
        assertEquals(expectedLeg ? transitLeg : null, position.getTransitLegWithClosestUpcomingOrigin());

        // Swap the two legs - result should be the same.
        position.nextLeg = transitLeg;
        position.expectedLeg = otherTransitLeg;
        assertEquals(expectedLeg ? transitLeg : null, position.getTransitLegWithClosestUpcomingOrigin());
    }

    static Stream<Arguments> createNearTransitLegOriginCases() {
        final int NORTH_EAST_BEARING = 45;
        Coordinates stopCoordinates = new Coordinates(33.78645, -84.381713);

        return Stream.of(
            Arguments.of(stopCoordinates, stopCoordinates, true),
            Arguments.of(stopCoordinates, createPoint(stopCoordinates, 4, NORTH_EAST_BEARING), true),
            Arguments.of(stopCoordinates, createPoint(stopCoordinates, 30, NORTH_EAST_BEARING), false)
        );
    }

    @ParameterizedTest
    @MethodSource("notifyWindowCases")
    void testIsWithinOperationalNotifyWindow(int beforeSeconds, int delaySeconds, boolean expected, String message) {
        Instant now = Instant.now();
        Leg leg = new Leg();
        leg.startTime = Date.from(now);
        leg.departureDelay = delaySeconds;

        assertEquals(
            expected,
            TravelerLocator.isWithinOperationalNotifyWindow(now.minusSeconds(beforeSeconds), leg),
            message
        );
    }

    private static Stream<Arguments> notifyWindowCases() {
        return Stream.of(
            Arguments.of(60, 0, true, "Before start time is in notify window."),
            Arguments.of(0, 0, false, "At start time is not in notify window."),
            Arguments.of(60, 60, true, "Should be indifferent to delays (they are included in startTime)."),
            Arguments.of(960, 0, true, "At the 15 min window (after truncations) is in notify window."),
            Arguments.of(970, 0, false, "Before the 15 min window is not in notify window."),
            Arguments.of(970, 60, true, "Before the 15 min window of actual departure but within 15 min after delays is in notify window."),
            Arguments.of(-60, 0, false, "After start time is not in notify window."),
            Arguments.of(-30, 60, false, "After start time with delay is not in notify window."),
            Arguments.of(-100, 60, false, "After start time with delay is not in notify window.")
        );
    }
}
