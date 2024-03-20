package org.opentripplanner.middleware.tripmonitor.jobs;

import org.junit.jupiter.params.ParameterizedTest;
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
    void testSkipMonitoredTripCheck(SkipMonitoringTestArgs args) throws Exception {
        MonitoredTrip trip = new MonitoredTrip();
        trip.itinerary = new Itinerary();
        trip.itinerary.legs = new ArrayList<>();
        Instant now = Instant.now();
        Date start = Date.from(now.plusSeconds(args.tripStartOffsetSecs));
        trip.itinerary.startTime = start;
        trip.itinerary.endTime = Date.from(now.plusSeconds(args.tripEndOffsetSecs));
        trip.tripTime = DateTimeUtils.makeOtpZonedDateTime(start).format(DateTimeFormatter.ISO_LOCAL_TIME);
        trip.leadTimeInMinutes = 30;

        assertEquals(
            args.result,
            new CheckMonitoredTrip(trip).shouldSkipMonitoredTripCheck(false),
            args.message
        );
    }

    private static Stream<SkipMonitoringTestArgs> createSkipMonitoringCases() {
        return Stream.of(
            new SkipMonitoringTestArgs(-300, -5, false, true, "Should skip monitoring one-time trip in the past"),
            new SkipMonitoringTestArgs(300, 500, false, false, "Should not skip monitoring upcoming one-time trip"),
            new SkipMonitoringTestArgs(3600, 3900, false, true, "Should skip monitoring one-time trip in the future")
        );
    }
}
