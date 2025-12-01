package org.opentripplanner.middleware.tripmonitor.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.tripmonitor.TripStatus;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils.createTransitWalkTransitItinerary;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getOtpZoneId;

/**
 * This class contains tests for {@link CheckMonitoredTrip} that don't require database or OTP queries.
 */
public class CheckMonitoredTripBasicTest {
    private static final int ONE_DAY_IN_SECONDS = 3600 * 24;

    @ParameterizedTest
    @MethodSource("createSkipMonitoringCases")
    void testSkipMonitoredTripCheck(SkipMonitoringTestArgs args) throws Exception {
        MonitoredTrip trip = makeMonitoredTripFromNow(args.tripStartOffsetSecs, args.tripEndOffsetSecs);
        if (args.isRecurring) {
            setRecurringTodayAndTomorrow(trip);
        }
        if (args.pastState) {
            trip.journeyState.tripStatus = TripStatus.PAST_TRIP;
        }
        assertEquals(
            args.expectedResult,
            new CheckMonitoredTrip(trip).shouldSkipMonitoredTripCheck(false),
            args.message
        );
    }

    private static Stream<SkipMonitoringTestArgs> createSkipMonitoringCases() {
        return Stream.of(
            new SkipMonitoringTestArgs(300, 500, false, false, "Should not skip monitoring upcoming one-time trip"),
            new SkipMonitoringTestArgs(3600, 3900, false, true, "Should skip monitoring one-time trip in the future"),
            new SkipMonitoringTestArgs(-300, -5, true, true, "Should skip monitoring recurring trip that just concluded for today"),
            new SkipMonitoringTestArgs(300, 500, false, false, "Should not skip monitoring upcoming recurring trip"),
            new SkipMonitoringTestArgs(3600, 3900, true, true, "Should skip monitoring recurring trip in the future"),
            new SkipMonitoringTestArgs(360 - ONE_DAY_IN_SECONDS, 500 - ONE_DAY_IN_SECONDS, true, false,
                "Should not skip recurring trip monitored on the following day"),
            new SkipMonitoringTestArgs(360 + ONE_DAY_IN_SECONDS, 500 + ONE_DAY_IN_SECONDS, true, true,
                "Should skip recurring trip not monitored today but that should be monitored if tomorrow."),
            new SkipMonitoringTestArgs(-300, -5, false, false, "Should not skip monitoring one-time trip in the past with no journey state"),
            new SkipMonitoringTestArgs(-300, -5, false, true, true, "Should skip monitoring one-time trip in the past with in-past status"),
            new SkipMonitoringTestArgs(360 - ONE_DAY_IN_SECONDS, 500 - ONE_DAY_IN_SECONDS, true, true, false,
                "Should not skip monitoring upcoming recurring trip with in-past status")
        );
    }

    /** Add the day-of-week of the itinerary start time as the recurring day, and the next day too. */
    public static void setRecurringTodayAndTomorrow(MonitoredTrip trip) {
        DayOfWeek dayOfWeek = DayOfWeek.of(LocalDate.ofInstant(
            trip.itinerary.startTime.toInstant(),
            getOtpZoneId()).get(ChronoField.DAY_OF_WEEK
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
        if (trip.itineraryExistence == null) {
            ItineraryExistence existence = new ItineraryExistence();
            existence.setResultForDayOfWeek(new ItineraryExistence.ItineraryExistenceResult(), dayOfWeek);
            existence.setResultForDayOfWeek(new ItineraryExistence.ItineraryExistenceResult(), dayOfWeek.plus(1));
            trip.itineraryExistence = existence;
        }
    }

    public static MonitoredTrip makeMonitoredTripFromNow(int startOffsetSecs, int endOffsetSecs) {
        Instant now = Instant.now();
        Date start = Date.from(now.plusSeconds(startOffsetSecs));

        Itinerary itinerary = new Itinerary();
        itinerary.legs = new ArrayList<>();
        itinerary.startTime = start;
        itinerary.endTime = Date.from(now.plusSeconds(endOffsetSecs));

        OtpGraphQLVariables params = new OtpGraphQLVariables();
        params.time = DateTimeUtils.makeOtpZonedDateTime(start).format(DateTimeFormatter.ISO_LOCAL_TIME);

        MonitoredTrip trip = new MonitoredTrip();
        trip.itinerary = itinerary;
        trip.otp2QueryParams = params;
        trip.leadTimeInMinutes = 30;
        return trip;
    }

    @ParameterizedTest
    @MethodSource("createFindEarliestTargetDateTime")
    void canFindEarliestTargetDateTime(int fromDay, int expectedDay, String message) {
        ZoneId zoneId = getOtpZoneId();
        // 10:25 am on some specified day in April 2025.
        ZonedDateTime fromDateTime = ZonedDateTime.of(2025, 4, fromDay, 10, 25, 0, 0, zoneId);

        OtpGraphQLVariables params = new OtpGraphQLVariables();
        params.time = "09:00";

        MonitoredTrip trip = makeMonitoredTripFromNow(300, 600);
        // Set the itinerary start time to 09:00 am, before the 'from' time above, on a different day (e.g. fromDay + 1).
        Instant itineraryStartInstant = fromDateTime.plusDays(1).withHour(9).withMinute(0).toInstant();
        trip.otp2QueryParams = params;
        trip.itinerary.startTime = Date.from(itineraryStartInstant);
        trip.itinerary.endTime = Date.from(itineraryStartInstant.plusSeconds(300));
        trip.updateAllDaysOfWeek(false);
        trip.monday = true;

        LocalDate expectedDate = fromDateTime.withDayOfMonth(expectedDay).toLocalDate();
        assertEquals(
            ZonedDateTime.of(expectedDate, LocalTime.parse(trip.otp2QueryParams.time, DateTimeFormatter.ISO_LOCAL_TIME), zoneId),
            trip.findEarliestTargetDate(fromDateTime),
            message
        );
    }

    private static Stream<Arguments> createFindEarliestTargetDateTime() {
        return Stream.of(
            Arguments.of(9, 14, "Wed Apr 9, 2025 should result in Monday Apr 14"),
            Arguments.of(14, 21, "Mon Apr 14, 2025 10am is after the trip and should result in next Monday")
        );
    }

    @ParameterizedTest
    @MethodSource("shouldSendInitialReminderCases")
    void testShouldSendInitialReminder(
        int offsetSeconds,
        boolean active,
        boolean snoozed,
        TripStatus tripStatus,
        boolean expected,
        String message
    ) throws CloneNotSupportedException {
        MonitoredTrip trip = makeMonitoredTripFromNow(offsetSeconds, offsetSeconds + 300);
        if (tripStatus != TripStatus.PAST_TRIP) {
            setRecurringTodayAndTomorrow(trip);
        } else {
            trip.updateAllDaysOfWeek(false);
        }
        trip.isActive = active;
        trip.snoozed = snoozed;
        trip.notifyAtLeadingInterval = true;
        trip.leadTimeInMinutes = 30;
        trip.journeyState.lastCheckedEpochMillis = Instant.now().minus(60, ChronoUnit.MINUTES).toEpochMilli();
        trip.journeyState.tripStatus = tripStatus;

        CheckMonitoredTrip check = new CheckMonitoredTrip(trip);
        check.matchingItinerary = trip.itinerary;
        check.previousMatchingItinerary = trip.itinerary;

        assertEquals(
            expected,
            check.shouldSendInitialReminder(),
            message
        );
    }

    static Stream<Arguments> shouldSendInitialReminderCases() {
        return Stream.of(
            Arguments.of(
                300,
                true,
                false,
                TripStatus.TRIP_UPCOMING,
                true,
                "Send reminder today for upcoming monitored trip today."
            ),
            Arguments.of(
                -60,
                true,
                false,
                TripStatus.TRIP_ACTIVE,
                true,
                "Send reminder for ongoing monitored trip (for trips saved after starting time)."
            ),
            Arguments.of(
                -60 + 24 * 3600, // Next trip starts tomorrow.
                true,
                false,
                TripStatus.TRIP_UPCOMING,
                false,
                "Don't send reminder today after today's trip is complete (send it tomorrow)."
            ),
            Arguments.of(
                300,
                false,
                true,
                TripStatus.TRIP_UPCOMING,
                false,
                "Don't send reminder for upcoming non-monitored trip."
            ),
            Arguments.of(
                300,
                true,
                true,
                TripStatus.TRIP_UPCOMING,
                false,
                "Don't send reminder for upcoming snoozed trip."
            ),
            Arguments.of(
                300,
                true,
                false,
                TripStatus.NEXT_TRIP_NOT_POSSIBLE,
                false,
                "Don't send reminder for unmonitorable trip."
            ),
            Arguments.of(
                -3000,
                true,
                false,
                TripStatus.PAST_TRIP,
                false,
                "Don't send reminder for one-time past trip."
            ),
            Arguments.of(
                -3000,
                true,
                false,
                TripStatus.NO_LONGER_POSSIBLE,
                false,
                "Don't send reminder for trip no longer possible."
            )
        );
    }

    @Test
    void canComputeTripDelays() throws CloneNotSupportedException {
        // Given a saved itinerary and some matching legs,
        // CheckMonitoredTrip should be able to compute trip delays.
        LocalDateTime baseTime = LocalDateTime.of(2025, 11, 10, 8, 0, 0);

        Itinerary itinerary = createTransitWalkTransitItinerary(baseTime);
        // Simulate a 6-minute delay on the itinerary arrival.
        // For the first leg, make it a 4-minute delay on departure only.
        final int DEPARTURE_DELAY_SECONDS = (int)Duration.ofMinutes(4).toSeconds();
        final int FINAL_DELAY_SECONDS = (int)Duration.ofMinutes(6).toSeconds();
        Itinerary mockItinerary = createTransitWalkTransitItinerary(baseTime.plusSeconds(FINAL_DELAY_SECONDS));
        Leg firstMockLeg = mockItinerary.legs.get(0);
        firstMockLeg.departureDelay = DEPARTURE_DELAY_SECONDS;
        firstMockLeg.realTime = true;
        firstMockLeg.startTime = convertToDate(LocalDateTime.ofInstant(firstMockLeg.startTime.toInstant(), getOtpZoneId()).minusMinutes(2));
        Leg lastMockLeg = mockItinerary.legs.get(2);
        lastMockLeg.arrivalDelay = FINAL_DELAY_SECONDS;
        lastMockLeg.realTime = true;

        MonitoredTrip trip = new MonitoredTrip();
        trip.itinerary = itinerary;

        MockLegResponseProvider mockLegResponseProvider = new MockLegResponseProvider(mockItinerary);
        LegFinder mockLegFinder = new LegFinder(mockLegResponseProvider::getLegResponse);

        CheckMonitoredTrip check = new CheckMonitoredTrip(trip, mockLegFinder);
        LegCheckStatus legStatus = check.checkLegs();

        assertTrue(legStatus.legsMatch);
        assertEquals(DEPARTURE_DELAY_SECONDS, legStatus.departureDelaySeconds);
        assertEquals(FINAL_DELAY_SECONDS, legStatus.arrivalDelaySeconds);
    }
}
