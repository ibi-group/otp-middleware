package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;

class ItineraryFromLegMatcherTest {
    @Test
    void canMatchItineraryFromLegs() {
        LocalDateTime baseTime = LocalDateTime.of(2025, 11, 10, 8, 0, 0);

        Itinerary itinerary = createTransitWalkTransitItinerary(baseTime);

        // Set up live (real-time) transit legs.
        // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
        // TODO: Handle cases with different leg start/end times (delays), and cases where some legs are null.
        Leg liveLeg1 = createBusLeg("transit-leg-id-1", baseTime, baseTime.plusMinutes(10));
        Leg liveLeg2 = createBusLeg("transit-leg-id-2", baseTime.plusMinutes(40), baseTime.plusMinutes(50));

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg1, liveLeg2));
        assertTrue(matcher.match(), "Transit legs in order should match.");

        ItineraryFromLegMatcher matcher1 = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg2, liveLeg1));
        assertFalse(matcher1.match(), "Transit legs out of order should not match.");

        ItineraryFromLegMatcher matcher2 = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg1));
        assertFalse(matcher2.match(),"Missing transit legs should not match.");
    }
}
