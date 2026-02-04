package org.opentripplanner.middleware.connecteddataplatform;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AnonymizedTripRequestTest {
    @Test
    void canCheckAllLastLegsTransit() {
        Leg transitLeg = new Leg();
        transitLeg.transitLeg = true;
        Leg walkLeg = new Leg();
        walkLeg.transitLeg = false;

        Itinerary itin1 = new Itinerary();
        itin1.legs = List.of(walkLeg);
        Itinerary itin2 = new Itinerary();
        itin2.legs = List.of(transitLeg, walkLeg);

        assertFalse(AnonymizedTripRequest.areAllFirstOrLastLegsTransit(List.of(itin1, itin2), false));
    }
}
