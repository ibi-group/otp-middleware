package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.opentripplanner.middleware.testutils.OtpTestUtils.createDefaultItinerary;

public class ItineraryMatcherTest {

    /**
     * Check whether certain itineraries match.
     */
    @ParameterizedTest
    @MethodSource("createItineraryComparisonTestCases")
    void testItineraryMatches(ItineraryMatchTestCase testCase) {
        ItineraryMatcher matcher = new ItineraryMatcher(testCase.previousItinerary, testCase.newItinerary);
        Assertions.assertEquals(testCase.shouldMatch, matcher.match(), testCase.name);
    }

    private static List<ItineraryMatchTestCase> createItineraryComparisonTestCases() throws Exception {
        List<ItineraryMatchTestCase> testCases = new ArrayList<>();

        // should match same data
        testCases.add(
            new ItineraryMatchTestCase(
                "Should be equal with same data",
                createDefaultItinerary(),
                true
            )
        );

        // should not be equal with a different amount of legs
        Leg extraBikeLeg = new Leg();
        extraBikeLeg.mode = "BICYCLE";
        Itinerary itineraryWithMoreLegs = createDefaultItinerary();
        itineraryWithMoreLegs.legs.add(extraBikeLeg);
        testCases.add(
            new ItineraryMatchTestCase(
                "should not be equal with a different amount of legs",
                itineraryWithMoreLegs,
                false
            )
        );

        // should be equal with realtime data on transit leg (same day)
        Itinerary itineraryWithRealtimeTransit = createDefaultItinerary();
        Leg transitLeg = itineraryWithRealtimeTransit.legs.get(1);
        int secondsOfDelay = 120;
        transitLeg.startTime = new Date(transitLeg.startTime.getTime() + secondsOfDelay * 1000);
        transitLeg.departureDelay = secondsOfDelay;
        transitLeg.endTime = new Date(transitLeg.endTime.getTime() + secondsOfDelay * 1000);
        transitLeg.arrivalDelay = secondsOfDelay;
        testCases.add(
            new ItineraryMatchTestCase(
                "should be equal with realtime data on transit leg (same day)",
                itineraryWithRealtimeTransit,
                true
            )
        );

        // should be equal with scheduled data on transit leg (future date)
        Itinerary itineraryOnFutureDate = createDefaultItinerary();
        Leg transitLeg2 = itineraryOnFutureDate.legs.get(1);
        transitLeg2.startTime = Date.from(transitLeg2.startTime.toInstant().plus(7, ChronoUnit.DAYS));
        transitLeg2.endTime = Date.from(transitLeg2.endTime.toInstant().plus(7, ChronoUnit.DAYS));
        testCases.add(
            new ItineraryMatchTestCase(
                "should be equal with scheduled data on transit leg (future date)",
                itineraryOnFutureDate,
                true
            )
        );

        return testCases;
    }

    private static class ItineraryMatchTestCase {
        /**
         * A descriptive name of this test case
         */
        public final String name;

        /**
         * The newer itinerary to compare to.
         */
        public final Itinerary newItinerary;

        /**
         * The previous itinerary which should be perform the baseline comparison from.
         */
        public final Itinerary previousItinerary;
        /**
         * Whether the given itineraries should match
         */
        public final boolean shouldMatch;

        /**
         * Constructor that uses the default itinerary as the previous itinerary.
         */
        public ItineraryMatchTestCase(
            String name,
            Itinerary newItinerary,
            boolean shouldMatch
        ) throws Exception {
            this(name, null, newItinerary, shouldMatch);
        }

        public ItineraryMatchTestCase(
            String name,
            Itinerary previousItinerary,
            Itinerary newItinerary,
            boolean shouldMatch
        ) throws Exception {
            this.name = name;
            if (previousItinerary != null) {
                this.previousItinerary = previousItinerary;
            } else {
                this.previousItinerary = createDefaultItinerary();
            }
            this.newItinerary = newItinerary;
            this.shouldMatch = shouldMatch;
        }
    }
}
