package org.opentripplanner.middleware.triptracker;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TravelerLocatorTest {
    @Test
    void testStopsUntilEndOfLeg() {
        Leg leg = new Leg();
        leg.intermediateStops = List.of(
            createPlace("Stop0"),
            createPlace("Stop1"),
            createPlace("Stop2"),
            createPlace("Stop3"),
            createPlace("Stop4"),
            createPlace("Stop5"),
            createPlace("Stop6")
        );

        for (int i = 0; i < 7; i++) {
            Place stop = leg.intermediateStops.get(i);
            assertEquals(7 - i, TravelerLocator.stopsUntilEndOfLeg(stop, leg), stop.stopId);
        }
    }

    Place createPlace(String id) {
        Place place = new Place();
        place.stopId = id;
        return place;
    }
}