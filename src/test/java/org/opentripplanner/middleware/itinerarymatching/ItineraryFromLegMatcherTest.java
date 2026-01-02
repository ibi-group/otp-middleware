package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createQueriedBusLeg;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createWalkTransitWalkTransitWalkItinerary;

class ItineraryFromLegMatcherTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2025, 11, 10, 8, 0, 0);
    private static final Itinerary ITINERARY = createTransitWalkTransitItinerary(BASE_TIME);

    // Set up live (real-time) transit legs.
    // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
    private static final Leg liveLeg1 = createQueriedBusLeg("transit-leg-id-1-expected", BASE_TIME, BASE_TIME.plusMinutes(10));
    private static final Leg liveLeg2 = createQueriedBusLeg("transit-leg-id-2-expected" ,BASE_TIME.plusMinutes(40), BASE_TIME.plusMinutes(50));
    public static final String LEG_ID_1 = "transit-leg-id-1";
    public static final String LEG_ID_2 = "transit-leg-id-2";

    @ParameterizedTest
    @MethodSource("itineraryFromLegsCases")
    void hasRequiredLegs(Map<String, Leg> legs, boolean isMatch, String message) {
        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(ITINERARY, legs);
        assertEquals(isMatch, matcher.hasRequiredLegs(), message);
    }

    private static Stream<Arguments> itineraryFromLegsCases() {
        Leg busLeg1WithDelays = createQueriedBusLeg("transit-leg-id-1-expected", BASE_TIME.plusMinutes(3), BASE_TIME.plusMinutes(15));
        busLeg1WithDelays.arrivalDelay = 300;
        busLeg1WithDelays.departureDelay = 180;

        return Stream.of(
            Arguments.of(
                Map.of(
                    LEG_ID_1, liveLeg1,
                    LEG_ID_2, liveLeg2
                ),
                true,
                "Transit legs should match."
            ),
            Arguments.of(Map.of(LEG_ID_1, liveLeg1), false, "Missing transit legs should not match."),
            Arguments.of(
                Map.of(
                    LEG_ID_1, liveLeg1,
                    LEG_ID_2, liveLeg2,
                    "extra-leg", createQueriedBusLeg("extra-leg-updated", BASE_TIME, BASE_TIME.plusMinutes(10))
                ),
                false,
                "Extra transit legs should not match."
            ),
            Arguments.of(
                Map.of(LEG_ID_1, busLeg1WithDelays, LEG_ID_2, liveLeg2),
                true,
                "Delayed transit legs should still match."
            )
        );
    }

    @Test
    void allLegsWithoutId() throws CloneNotSupportedException {
        // This test covers itineraries saved before we actively query/save leg ids.

        Itinerary itinerary = ITINERARY.clone();
        itinerary.legs.forEach(leg -> leg.id = null);

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            itinerary,
            Map.of(LEG_ID_1, liveLeg1, LEG_ID_2, liveLeg2)
        );
        assertFalse(matcher.hasRequiredLegs(), "Itinerary legs without ids should not match.");
    }

    @Test
    void canRebuildItineraryFromLegs() {
        // An itinerary rebuilt from an itinerary on a different day
        // should get the correct day and time.
        LocalDateTime yesterday = BASE_TIME.minusDays(1);
        Itinerary itinerary = createWalkTransitWalkTransitWalkItinerary(yesterday);

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            itinerary,
            Map.of(LEG_ID_1, liveLeg1, LEG_ID_2, liveLeg2)
        );
        ItineraryCheckStatus matcherResult = matcher.process();
        Itinerary rebuiltItinerary = matcherResult.rebuiltItinerary;
        assertTrue(matcher.processed());
        assertTrue(matcherResult.legsMatch);
        assertFalse(matcherResult.impossibleTransfer);
        assertNull(matcherResult.exception);
        assertFalse(matcherResult.isFailed());

        ItineraryMatcher classicMatcher = new ItineraryMatcher(itinerary, rebuiltItinerary);
        assertTrue(classicMatcher.match(), classicMatcher.getFailingReason());

        // Itinerary should have received the updated legs.
        assertEquals(liveLeg1, rebuiltItinerary.legs.get(1));
        assertEquals(liveLeg2, rebuiltItinerary.legs.get(3));
        // All legs should have been shifted
        for (Leg leg : rebuiltItinerary.legs) {
            assertEquals(BASE_TIME.toLocalDate(), LocalDate.ofInstant(leg.startTime.toInstant(), DateTimeUtils.getOtpZoneId()));
        }
        // Itinerary start, end time should have been updated.
        assertEquals(DateTimeUtils.convertToDate(BASE_TIME.minusMinutes(10)), rebuiltItinerary.startTime);
        assertEquals(DateTimeUtils.convertToDate(BASE_TIME.plusMinutes(55)), rebuiltItinerary.endTime);
    }

    @Test
    void shouldNotRebuildItineraryIfMissingLegIds() {
        LocalDateTime yesterday = BASE_TIME.minusDays(1);
        Itinerary itinerary = createWalkTransitWalkTransitWalkItinerary(yesterday);

        // An itinerary where leg ids are null should not get rebuilt.
        itinerary.legs.forEach(leg -> leg.id = null);

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            itinerary,
            Map.of(LEG_ID_1, liveLeg1, LEG_ID_2, liveLeg2)
        );
        ItineraryCheckStatus matcherResult = matcher.process();
        assertTrue(matcher.processed());
        assertTrue(matcherResult.isFailed());
        assertFalse(matcherResult.legsMatch);
    }

    @Test
    void boardingSlack() {
        Itinerary itinerary = createWalkTransitWalkTransitWalkItinerary(LocalDateTime.now());
        assertEquals(
            Duration.between(
                itinerary.legs.get(0).endTime.toInstant(),
                itinerary.legs.get(1).startTime.toInstant()
            ),
            ItineraryFromLegMatcher.computeBoardingSlack(itinerary)
        );
    }

    @Test
    void boardingSlackTransitWalkItinerary() {
        Itinerary itinerary = createTransitWalkTransitItinerary(LocalDateTime.now());
        assertEquals(Duration.ZERO, ItineraryFromLegMatcher.computeBoardingSlack(itinerary));
    }

    @Test
    void alightingSlack() {
        Itinerary itinerary = createWalkTransitWalkTransitWalkItinerary(LocalDateTime.now());
        assertEquals(
            Duration.between(
                itinerary.legs.get(1).endTime.toInstant(),
                itinerary.legs.get(2).startTime.toInstant()
            ),
            ItineraryFromLegMatcher.computeAlightingSlack(itinerary)
        );
    }

    @Test
    void alightingSlackNoAccessLeg() {
        Itinerary itinerary = createTransitWalkTransitItinerary(LocalDateTime.now());
        itinerary.legs.remove(0);
        assertEquals(Duration.ZERO, ItineraryFromLegMatcher.computeAlightingSlack(itinerary));
    }
}
