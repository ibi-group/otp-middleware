package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.tripmonitor.jobs.MockLegResponseProvider;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg1;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createBusLeg2;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createWalkLeg;

class ItineraryFromLegMatcherTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2025, 11, 10, 8, 0, 0);
    private static final Itinerary ITINERARY = createTransitWalkTransitItinerary(BASE_TIME);

    // Set up live (real-time) transit legs.
    // Non-null transit legs are presumed to match origin, destination, and trip id on a given transit route.
    // TODO: Handle cases with different leg start/end times (delays), and cases where some legs are null.
    private static final Leg liveLeg1 = createBusLeg1(BASE_TIME, BASE_TIME.plusMinutes(10));
    private static final Leg liveLeg2 = createBusLeg2(BASE_TIME.plusMinutes(40), BASE_TIME.plusMinutes(50));

    static {
        liveLeg1.id = MockLegResponseProvider.makeUpdatedLegId(liveLeg1);
        liveLeg2.id = MockLegResponseProvider.makeUpdatedLegId(liveLeg2);
    }

    @ParameterizedTest
    @MethodSource("itineraryFromLegsCases")
    void hasRequiredLegs(Collection<Leg> legs, boolean isMatch, String message) {
        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            ITINERARY,
            legs,
            MockLegResponseProvider.makeUpdatedLegIdMap(getTransitLegs(ITINERARY.legs))
        );
        assertEquals(isMatch, matcher.hasRequiredLegs(), message);
    }

    private static Stream<Arguments> itineraryFromLegsCases() {
        return Stream.of(
            Arguments.of(List.of(liveLeg1, liveLeg2), true, "Transit legs in order should match."),
            Arguments.of(List.of(liveLeg2, liveLeg1), true, "Transit legs out of order should still match."),
            Arguments.of(List.of(liveLeg1), false, "Missing transit legs should not match.")
        );
    }

    @Test
    void unmappedLeg() {
        List<Leg> knownLegs = List.of(liveLeg1, liveLeg2);
        Leg otherLeg = new Leg();
        otherLeg.id = "other-leg";
        otherLeg.transitLeg = true;
        List<Leg> providedLegs = List.of(liveLeg1, otherLeg);

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            ITINERARY,
            providedLegs,
            MockLegResponseProvider.makeUpdatedLegIdMap(knownLegs)
        );
        assertFalse(matcher.hasRequiredLegs(), "Updated legs must map to original legs.");
    }

    @Test
    void canRebuildItineraryFromLegs() {
        // An itinerary rebuilt from an itinerary on a different day
        // should get the correct day and time.
        LocalDateTime yesterday = BASE_TIME.minusDays(1);
        Itinerary itinerary = createTransitWalkTransitItinerary(yesterday);
        // Insert initial walk leg
        Leg initialWalkLeg =  createWalkLeg(yesterday.minusMinutes(10), yesterday.minusMinutes(5));
        initialWalkLeg.from = initialWalkLeg.to = itinerary.legs.get(0).to;
        itinerary.legs.add(0, initialWalkLeg);
        // Insert final walk leg
        Leg finalWalkLeg =  createWalkLeg(yesterday.plusMinutes(50), yesterday.plusMinutes(55));
        finalWalkLeg.from = finalWalkLeg.to = itinerary.legs.get(3).to;
        itinerary.legs.add(finalWalkLeg);

        ItineraryFromLegMatcher matcher = new ItineraryFromLegMatcher(
            itinerary,
            List.of(liveLeg1, liveLeg2),
            MockLegResponseProvider.makeUpdatedLegIdMap(getTransitLegs(itinerary.legs))
        );
        ItineraryCheckStatus matcherResult = matcher.process();
        Itinerary rebuiltItinerary = matcherResult.rebuiltItinerary;
        assertTrue(matcher.processed());
        assertTrue(matcherResult.legsMatch);
        assertFalse(matcherResult.impossibleTransfer);
        assertNull(matcherResult.exception);
        assertFalse(matcherResult.isBogus());

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
}
