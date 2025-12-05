package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg1;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg2;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;

class ItineraryFromLegMatcherTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2025, 11, 10, 8, 0, 0);
    private static final Itinerary ITINERARY = createTransitWalkTransitItinerary(BASE_TIME);

    // Set up live (real-time) transit legs.
    // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
    // TODO: Handle cases with different leg start/end times (delays), and cases where some legs are null.
    private static final Leg liveLeg1 = createBusLeg1(BASE_TIME, BASE_TIME.plusMinutes(10));
    private static final Leg liveLeg2 = createBusLeg2(BASE_TIME.plusMinutes(40), BASE_TIME.plusMinutes(50));

    @ParameterizedTest
    @MethodSource("itineraryFromLegsCases")
    void canMatchItineraryFromLegs(Collection<Leg> legs, boolean isMatch, String message) {
        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(ITINERARY, legs);
        assertEquals(isMatch, matcher.match(), message);
    }

    private static Stream<Arguments> itineraryFromLegsCases() {
        return Stream.of(
            Arguments.of(List.of(liveLeg1, liveLeg2), true, "Transit legs in order should match."),
            Arguments.of(List.of(liveLeg2, liveLeg1), false, "Transit legs out of order should not match."),
            Arguments.of(List.of(liveLeg1), false, "Missing transit legs should not match.")
        );
    }

    @Test
    void canRebuildItineraryFromLegs() {
        // An itinerary rebuilt from an itinerary on a different day
        // should get the correct day and time.
        Itinerary itinerary = createTransitWalkTransitItinerary(BASE_TIME.minusDays(1));

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(itinerary, List.of(liveLeg1, liveLeg2));
        Itinerary rebuiltItinerary = matcher.getRebuiltItinerary();

        ItineraryMatcher classicMatcher = new ItineraryMatcher(itinerary, rebuiltItinerary);
        assertTrue(classicMatcher.match(), classicMatcher.getFailingReason());

        assertEquals(liveLeg1, rebuiltItinerary.legs.get(0));
        assertEquals(liveLeg2, rebuiltItinerary.legs.get(2));
        assertEquals(DateTimeUtils.convertToDate(BASE_TIME), rebuiltItinerary.startTime);
        assertEquals(DateTimeUtils.convertToDate(BASE_TIME.plusMinutes(50)), rebuiltItinerary.endTime);
    }
}
