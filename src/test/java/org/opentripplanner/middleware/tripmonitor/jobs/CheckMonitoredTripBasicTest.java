package org.opentripplanner.middleware.tripmonitor.jobs;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This class contains tests for {@link CheckMonitoredTrip} that don't require database or OTP queries.
 */
class CheckMonitoredTripBasicTest {
    private static final int ONE_DAY_IN_SECONDS = 3600 * 24;

    @ParameterizedTest
    @MethodSource("createSkipMonitoringCases")
    void testSkipMonitoredTripCheck(SkipMonitoringTestArgs args) throws Exception {
        Instant now = Instant.now();
        Date start = Date.from(now.plusSeconds(args.tripStartOffsetSecs));

        Itinerary itinerary = new Itinerary();
        itinerary.legs = new ArrayList<>();
        itinerary.startTime = start;
        itinerary.endTime = Date.from(now.plusSeconds(args.tripEndOffsetSecs));

        MonitoredTrip trip = new MonitoredTrip();
        trip.itinerary = itinerary;
        trip.tripTime = DateTimeUtils.makeOtpZonedDateTime(start).format(DateTimeFormatter.ISO_LOCAL_TIME);
        trip.leadTimeInMinutes = 30;
        if (args.isRecurring) {
            setMonitoredDaysForTest(trip);
        }

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
            new SkipMonitoringTestArgs(3600, 3900, false, true, "Should skip monitoring one-time trip in the future"),
            new SkipMonitoringTestArgs(-300, -5, true, true, "Should skip monitoring recurring trip that just concluded for today"),
            new SkipMonitoringTestArgs(300, 500, false, false, "Should not skip monitoring upcoming recurring trip"),
            new SkipMonitoringTestArgs(3600, 3900, true, true, "Should skip monitoring recurring trip in the future"),
            new SkipMonitoringTestArgs(360 - ONE_DAY_IN_SECONDS, 500 - ONE_DAY_IN_SECONDS, true, false,
                "Should not skip recurring trip monitored on the following day"),
            new SkipMonitoringTestArgs(360 + ONE_DAY_IN_SECONDS, 500 + ONE_DAY_IN_SECONDS, true, true,
                "Should skip recurring trip not monitored today but that should be monitored if tomorrow.")
        );
    }

    /** Add the day-of-week of the itinerary start time as the recurring day, and the next day too. */
    private static void setMonitoredDaysForTest(MonitoredTrip trip) {
        DayOfWeek dayOfWeek = DayOfWeek.of(LocalDate.ofInstant(
            trip.itinerary.startTime.toInstant(),
            DateTimeUtils.getOtpZoneId()).get(ChronoField.DAY_OF_WEEK
        ));
        switch (dayOfWeek) {
            case MONDAY:
                trip.monday = true;
                trip.tuesday = true;
                break;
            case TUESDAY:
                trip.tuesday = true;
                trip.wednesday = true;
                break;
            case WEDNESDAY:
                trip.wednesday = true;
                trip.thursday = true;
                break;
            case THURSDAY:
                trip.thursday = true;
                trip.friday = true;
                break;
            case FRIDAY:
                trip.friday = true;
                trip.saturday = true;
                break;
            case SATURDAY:
                trip.saturday = true;
                trip.sunday = true;
                break;
            case SUNDAY:
                trip.sunday = true;
                trip.monday = true;
                break;
            default:
                break;
        }
    }
}
