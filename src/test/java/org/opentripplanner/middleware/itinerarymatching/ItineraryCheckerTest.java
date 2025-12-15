package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.tripmonitor.jobs.MockLegResponseProvider;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getOtpZoneId;

class ItineraryCheckerTest {
    @Test
    void canComputeTripDelays() {
        // Given a saved itinerary and some matching legs,
        // CheckMonitoredTrip should be able to compute trip delays.
        LocalDateTime baseTime = LocalDateTime.of(2025, 11, 10, 8, 0, 0);

        Itinerary itinerary = createTransitWalkTransitItinerary(baseTime);
        // Simulate a 6-minute delay on the itinerary arrival.
        // For the first leg, make it a 4-minute delay on departure only.
        final int DEPARTURE_DELAY_SECONDS = (int) Duration.ofMinutes(4).toSeconds();
        final int FINAL_DELAY_SECONDS = (int)Duration.ofMinutes(6).toSeconds();
        Itinerary mockItinerary = createTransitWalkTransitItinerary(baseTime.plusSeconds(FINAL_DELAY_SECONDS));
        Leg firstMockLeg = mockItinerary.legs.get(0);
        firstMockLeg.departureDelay = DEPARTURE_DELAY_SECONDS;
        firstMockLeg.realTime = true;
        firstMockLeg.startTime = convertToDate(LocalDateTime.ofInstant(firstMockLeg.startTime.toInstant(), getOtpZoneId()).minusMinutes(2));
        Leg lastMockLeg = mockItinerary.legs.get(2);
        lastMockLeg.arrivalDelay = FINAL_DELAY_SECONDS;
        lastMockLeg.realTime = true;

        MockLegResponseProvider mockLegResponseProvider = new MockLegResponseProvider(mockItinerary);
        LegFinder mockLegFinder = new LegFinder(
            mockLegResponseProvider::getLegResponse,
            MockLegResponseProvider::computeLegIdForServiceDate
        );

        ItineraryChecker checker = new ItineraryChecker(itinerary, mockLegFinder, DateTimeUtils.nowAsZonedDateTime().toLocalDate());
        ItineraryCheckStatus itineraryCheckStatus = checker.checkLegs();

        assertTrue(itineraryCheckStatus.legsMatch);
        assertEquals(DEPARTURE_DELAY_SECONDS, itineraryCheckStatus.departureDelaySeconds);
        assertEquals(FINAL_DELAY_SECONDS, itineraryCheckStatus.arrivalDelaySeconds);
    }
}
