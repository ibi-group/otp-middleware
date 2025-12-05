package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;

class ItineraryFromLegMatcherTest {

    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2025, 11, 10, 8, 0, 0);

    @ParameterizedTest
    @MethodSource("itineraryFromLegsCases")
    void canMatchItineraryFromLegs(Collection<Leg> legs, boolean isMatch, String message) {
        Itinerary itinerary = createTransitWalkTransitItinerary(BASE_TIME);
        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(itinerary, legs);
        assertEquals(isMatch, matcher.match(), message);
    }

    private static Stream<Arguments> itineraryFromLegsCases() {
        // Set up live (real-time) transit legs.
        // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
        // TODO: Handle cases with different leg start/end times (delays), and cases where some legs are null.
        Leg liveLeg1 = createBusLeg("transit-leg-id-1", BASE_TIME, BASE_TIME.plusMinutes(10));
        Leg liveLeg2 = createBusLeg("transit-leg-id-2", BASE_TIME.plusMinutes(40), BASE_TIME.plusMinutes(50));

        return Stream.of(
            Arguments.of(List.of(liveLeg1, liveLeg2), true, "Transit legs in order should match."),
            Arguments.of(List.of(liveLeg2, liveLeg1), false, "Transit legs out of order should not match."),
            Arguments.of(List.of(liveLeg1), false, "Missing transit legs should not match.")
        );
    }
}
