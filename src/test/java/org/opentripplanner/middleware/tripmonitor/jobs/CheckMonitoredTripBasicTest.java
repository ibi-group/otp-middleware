package org.opentripplanner.middleware.tripmonitor.jobs;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This class contains tests for {@link CheckMonitoredTrip} that don't require database or OTP queries.
 */
class CheckMonitoredTripBasicTest {
    @ParameterizedTest
    @MethodSource("createSkipMonitoringCases")
    void testSkipMonitoredTripCheck(
        int tripStartOffsetSecs,
        int tripEndOffsetSecs,
        boolean result,
        String message
    ) throws Exception {
        MonitoredTrip trip = new MonitoredTrip();
        trip.itinerary = new Itinerary();
        trip.itinerary.legs = new ArrayList<>();
        Instant now = Instant.now();
        trip.itinerary.startTime = Date.from(now.plusSeconds(tripStartOffsetSecs));
        trip.itinerary.endTime = Date.from(now.plusSeconds(tripEndOffsetSecs));
        trip.tripTime = DateTimeUtils.makeOtpZonedDateTime(trip.itinerary.startTime).format(DateTimeFormatter.ISO_LOCAL_TIME);
        trip.leadTimeInMinutes = 30;

        assertEquals(result, new CheckMonitoredTrip(trip).shouldSkipMonitoredTripCheck(false), message);
    }

    static Stream<Arguments> createSkipMonitoringCases() {
        return Stream.of(
            Arguments.of(-300, -5, true, "Should skip monitoring one-time trip in the past"),
            Arguments.of(300, 500, false, "Should not skip monitoring upcoming one-time trip"),
            Arguments.of(3600, 3900, true, "Should skip monitoring one-time trip in the future")
        );
    }
}
