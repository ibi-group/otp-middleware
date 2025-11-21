package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;

class ItineraryFromLegMatcherTest {
    @Test
    void canMatchItineraryFromLegs() {
        // Create a {transit, walk, transit} reference itinerary.
        Itinerary itinerary = new Itinerary();
        Leg transitLeg1 = new Leg();
        transitLeg1.transitLeg = true;
        transitLeg1.mode = "BUS";
        transitLeg1.id = "transit-leg-id-1";
        transitLeg1.startTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 0, 0));
        transitLeg1.endTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 10, 0));

        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";
        walkLeg.startTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 20, 0));
        walkLeg.endTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 30, 0));

        Leg transitLeg2 = new Leg();
        transitLeg2.transitLeg = true;
        transitLeg2.mode = "BUS";
        transitLeg2.id = "transit-leg-id-2";
        transitLeg2.startTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 40, 0));
        transitLeg2.endTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 50, 0));

        itinerary.legs = List.of(transitLeg1, walkLeg, transitLeg2);

        // Set up live (real-time) transit legs.
        // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
        // TODO: Handle cases with different leg start/end times (delays), and cases where some legs are null.
        Leg liveLeg1 = new Leg();
        liveLeg1.id = "transit-leg-id-1";
        liveLeg1.transitLeg = true;
        liveLeg1.mode = "BUS";
        liveLeg1.startTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 0, 0));
        liveLeg1.endTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 10, 0));

        Leg liveLeg2 = new Leg();
        liveLeg2.id = "transit-leg-id-2";
        liveLeg2.transitLeg = true;
        liveLeg2.mode = "BUS";
        liveLeg2.startTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 40, 0));
        liveLeg2.endTime = convertToDate(LocalDateTime.of(2025, 11, 10, 8, 50, 0));

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg1, liveLeg2));
        assertTrue(matcher.match());

        ItineraryFromLegMatcher matcher1 = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg2, liveLeg1));
        assertFalse(matcher1.match());

        ItineraryFromLegMatcher matcher2 = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg1));
        assertFalse(matcher2.match());
    }
}
