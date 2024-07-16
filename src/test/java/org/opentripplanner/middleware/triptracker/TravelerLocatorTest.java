package org.opentripplanner.middleware.triptracker;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.stopsUntilEndOfLeg;

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
}
