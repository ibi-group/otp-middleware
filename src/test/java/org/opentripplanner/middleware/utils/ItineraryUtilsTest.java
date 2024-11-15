package org.opentripplanner.middleware.utils;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpGraphQLTransportMode;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.utils.DateTimeUtils.otpDateTimeAsEpochMillis;

public class ItineraryUtilsTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryUtilsTest.class);
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    /** Date and time from the above query. */
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

    public static final List<String> MONITORED_TRIP_DATES = List.of(
        QUERY_DATE, "2020-08-14", "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18", "2020-08-19"
    );

    // Indexes for the days above: Thursday is 0, ..., Wednesday is 6.
    private static final int FRIDAY_INDEX = 1;
    private static final int MONDAY_INDEX = 4;
    private static final int WEDNESDAY_INDEX = 6;

    /** Timestamps (in OTP's timezone) to test whether an itinerary is same-day as QUERY_DATE. */

    // Aug 12, 2020 3:00:00 AM
    public static final long _2020_08_12__03_00_00 = otpDateTimeAsEpochMillis(
        2020, 8, 12, 3, 0, 0
    );

    // Aug 12, 2020 11:59:59 PM
    public static final long _2020_08_12__23_59_59 = otpDateTimeAsEpochMillis(
        2020,8,12,23,59,59
    );

    // Aug 13, 2020 2:59:59 AM, considered to be Aug 12.
    public static final long _2020_08_13__02_59_59 = otpDateTimeAsEpochMillis(
        2020, 8, 13, 2, 59, 59
    );

    // Aug 13, 2020 3:00:00 AM
    public static final long _2020_08_13__03_00_00 = otpDateTimeAsEpochMillis(
        2020, 8, 13, 3, 0, 0
    );

    // Aug 13, 2020 11:59:59 PM
    public static final long _2020_08_13__23_59_59 = otpDateTimeAsEpochMillis(
        2020, 8, 13, 23, 59, 59
    );

    // Aug 14, 2020 2:59:59 AM, considered to be Aug 13.
    public static final long _2020_08_14__02_59_59 = otpDateTimeAsEpochMillis(
        2020, 8, 14, 2, 59, 59
    );

    // Aug 14, 2020 3:00:00 AM
    public static final long _2020_08_14__03_00_00 = otpDateTimeAsEpochMillis(
        2020, 8, 14, 3, 0, 0
    );

    /** Contains the verified itinerary set for a trip upon persisting. */
    public static Itinerary getDefaultItinerary() throws Exception {
        return OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getResponse().plan.itineraries.get(0);
    }

    /**
     * Test that itineraries exist and result.allCheckedDatesAreValid are as expected.
     */
    @ParameterizedTest
    @MethodSource("createCheckAllItinerariesExistTestCases")
    void canCheckAllItinerariesExist(boolean insertInvalidDay, String message) throws Exception {
        MonitoredTrip trip = makeTestTrip();
        List<OtpResponse> mockOtpResponses = getMockDatedOtpResponses(MONITORED_TRIP_DATES);

        // If needed, insert a mock invalid response for one of the monitored days.
        if (insertInvalidDay) {
            mockOtpResponses.set(MONDAY_INDEX, OtpTestUtils.OTP_DISPATCHER_PLAN_ERROR_RESPONSE.getResponse());
        }
        // Return an erroneous response for some days that are not monitored (Wednesday, Friday).
        mockOtpResponses.set(FRIDAY_INDEX, OtpTestUtils.OTP_DISPATCHER_PLAN_ERROR_RESPONSE.getResponse());
        mockOtpResponses.set(WEDNESDAY_INDEX, OtpTestUtils.OTP_DISPATCHER_PLAN_ERROR_RESPONSE.getResponse());

        MockOtpResponseProvider mockResponses = new MockOtpResponseProvider(mockOtpResponses);

        // Also set trip itinerary to the template itinerary for easy/lazy match.
        Itinerary expectedItinerary = mockOtpResponses.get(0).plan.itineraries.get(0);
        trip.itinerary = expectedItinerary;

        trip.checkItineraryExistence(false, mockResponses::getMockResponse);
        ItineraryExistence existence = trip.itineraryExistence;

        boolean allDaysValid = !insertInvalidDay;
        Assertions.assertEquals(allDaysValid, existence.allMonitoredDaysAreValid(trip), message);

        // Valid days, in the order of the OTP requests sent (so we can track the invalid entry if we inserted one).
        ArrayList<ItineraryExistence.ItineraryExistenceResult> validDays = Lists.newArrayList(
            existence.thursday,
            existence.saturday,
            existence.sunday,
            existence.monday,
            existence.tuesday
        );
        // Check (and remove) the extra invalid day (monday) if we inserted one above.
        if (insertInvalidDay) {
            assertFalse(existence.monday.isValid());
            validDays.remove(existence.monday);
        }

        // FIXME: For now, just check that the first itinerary in the list is valid. If we expand our check window from
        //  7 days to 14 (or more) days, this may need to be adjusted.
        for (ItineraryExistence.ItineraryExistenceResult validDay : validDays) {
            assertTrue(validDay.isValid());
            assertTrue(ItineraryUtils.itinerariesMatch(expectedItinerary, validDay.itineraries.get(0)));
        }

        // Days not monitored had an error response, so the check should return invalid for those days.
        assertFalse(existence.wednesday.isValid());
        assertFalse(existence.friday.isValid());

        // Make sure all mocks were used
        assertTrue(mockResponses.areAllMocksUsed());
    }

    private static Stream<Arguments> createCheckAllItinerariesExistTestCases() {
        return Stream.of(
            Arguments.of(false, "checkAllDays = false should produce allCheckedDaysAreValid = true."),
            Arguments.of(true, "checkAllDays = true should produce allCheckedDaysAreValid = false.")
        );
    }

    /**
     * @return The new {@link Date} object with the date portion set to the specified {@link LocalDate} in OTP timezone.
     */
    private static Date getNewItineraryDate(Date itineraryDate, LocalDate date) {
        return new Date(
            ZonedDateTime.ofInstant(itineraryDate.toInstant(), DateTimeUtils.getOtpZoneId())
                .with(date)
                .toInstant()
                .toEpochMilli()
        );
    }

    /**
     * Creates a set of mock OTP responses by making copies of #OTP_DISPATCHER_PLAN_RESPONSE,
     * each copy having the itinerary date set to one of the dates from the specified dates list.
     */
    public static List<OtpResponse> getMockDatedOtpResponses(List<String> dates) throws Exception {
        // Set mocks to a list of responses with itineraries, ordered by day.
        List<OtpResponse> mockOtpResponses = new ArrayList<>();

        for (String dateString : dates) {
            LocalDate monitoredDate = LocalDate.parse(dateString, DateTimeUtils.DEFAULT_DATE_FORMATTER);

            // Copy the template OTP response itinerary, and change the itinerary date to the monitored date,
            // in order to pass the same-day itinerary requirement.
            OtpResponse resp = OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getResponse();
            for (Itinerary itin : resp.plan.itineraries) {
                itin.startTime = getNewItineraryDate(itin.startTime, monitoredDate);
                itin.endTime = getNewItineraryDate(itin.endTime, monitoredDate);
            }

            mockOtpResponses.add(resp);
        }
        return mockOtpResponses;
    }

    /**
     * Check that the query date parameter is properly modified to simulate the given OTP query for different dates.
     */
    @Test
    void canGetQueriesFromDates() {
        MonitoredTrip trip = makeTestTrip();
        // Create test dates.
        List<String> testDateStrings = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        LOG.info(String.join(", ", testDateStrings));
        List<ZonedDateTime> testDates = datesToZonedDateTimes(testDateStrings);
        // Get OTP requests modified with dates.
        List<OtpRequest> requests = ItineraryUtils.getOtpRequestsForDates(trip.otp2QueryParams, testDates);
        Assertions.assertEquals(testDateStrings.size(), requests.size());
        // Iterate over OTP requests and verify that query dates match the input.
        for (int i = 0; i < testDates.size(); i++) {
            ZonedDateTime testDate = testDates.get(i);
            OtpGraphQLVariables newParams = requests.get(i).requestParameters;
            Assertions.assertEquals(
                testDate.format(DateTimeUtils.DEFAULT_DATE_FORMATTER),
                newParams.date
            );
        }
    }

    /**
     * Check the computation of the dates corresponding to the monitored days,
     * for which we want to check itinerary existence.
     */
    @Test
    void canGetDatesToCheckItineraryExistence() {
        MonitoredTrip trip = makeTestTrip();
        List<ZonedDateTime> testDates = datesToZonedDateTimes(MONITORED_TRIP_DATES);
        List<ZonedDateTime> datesToCheck = ItineraryUtils.getDatesToCheckItineraryExistence(trip);
        Assertions.assertEquals(testDates, datesToCheck);
    }

    /**
     * Check whether certain itineraries match.
     */
    @ParameterizedTest
    @MethodSource("createItineraryComparisonTestCases")
    void testItineraryMatches(ItineraryMatchTestCase testCase) {
        Assertions.assertEquals(
            testCase.shouldMatch,
            ItineraryUtils.itinerariesMatch(testCase.previousItinerary, testCase.newItinerary),
            testCase.name
        );
    }

    private static List<ItineraryMatchTestCase> createItineraryComparisonTestCases() throws Exception {
        List<ItineraryMatchTestCase> testCases = new ArrayList<>();

        // should match same data
        testCases.add(
            new ItineraryMatchTestCase(
                "Should be equal with same data",
                getDefaultItinerary().clone(),
                true
            )
        );

        // should not be equal with a different amount of legs
        Leg extraBikeLeg = new Leg();
        extraBikeLeg.mode = "BICYCLE";
        Itinerary itineraryWithMoreLegs = getDefaultItinerary().clone();
        itineraryWithMoreLegs.legs.add(extraBikeLeg);
        testCases.add(
            new ItineraryMatchTestCase(
                "should not be equal with a different amount of legs",
                itineraryWithMoreLegs,
                false
            )
        );

        // should be equal with realtime data on transit leg (same day)
        Itinerary itineraryWithRealtimeTransit = getDefaultItinerary().clone();
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
        Itinerary itineraryOnFutureDate = getDefaultItinerary().clone();
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

    /**
     * Helper method to create a trip with locations, time, and queryParams populated.
     */
    private MonitoredTrip makeTestTrip() {
        Place targetPlace = new Place();
        targetPlace.lat = 33.80;
        targetPlace.lon = -84.70; // America/New_York

        Place dummyPlace = new Place();
        dummyPlace.lat = 33.90;
        dummyPlace.lon = 0.0; // Africa/Algiers.

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "Test trip";
        trip.queryParams = BASE_QUERY;
        trip.otp2QueryParams = new OtpGraphQLVariables();
        trip.otp2QueryParams.date = QUERY_DATE;
        trip.otp2QueryParams.mobilityProfile = "mobility-profile";
        trip.otp2QueryParams.time = QUERY_TIME;
        trip.tripTime = QUERY_TIME;

        trip.from = targetPlace;
        trip.to = dummyPlace;

        // trip monitored days.
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        return trip;
    }

    /**
     * Converts a list of date strings to a set of {@link ZonedDateTime} assuming QUERY_TIME.
     */
    static List<ZonedDateTime> datesToZonedDateTimes(List<String> dates) {
        return dates.stream()
            .map(d -> DateTimeUtils.makeOtpZonedDateTime(d, QUERY_TIME))
            .collect(Collectors.toList());
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
                this.previousItinerary = getDefaultItinerary();
            }
            this.newItinerary = newItinerary;
            this.shouldMatch = shouldMatch;
        }
    }

    @ParameterizedTest
    @MethodSource("createSameServiceDayTestCases")
    void canCheckOccursOnSameServiceDay(SameDayTestCase testCase) {
        Itinerary itinerary = simpleItinerary(testCase.tripTargetTimeEpochMillis, testCase.isArriveBy);

        ZonedDateTime queryDateTime = ZonedDateTime.of(
            LocalDate.parse(QUERY_DATE, DateTimeUtils.DEFAULT_DATE_FORMATTER),
            LocalTime.parse(testCase.timeOfDay, DateTimeFormatter.ofPattern("H:mm")),
            DateTimeUtils.getOtpZoneId()
        );

        Assertions.assertEquals(
            testCase.shouldBeSameDay,
            ItineraryUtils.occursOnSameServiceDay(itinerary, queryDateTime, testCase.isArriveBy),
            testCase.getMessage(DateTimeUtils.getOtpZoneId())
        );
    }

    private static List<SameDayTestCase> createSameServiceDayTestCases() {
        return List.of(
            // Same-day departures
            new SameDayTestCase(QUERY_TIME, _2020_08_13__03_00_00, false, true),
            new SameDayTestCase(QUERY_TIME, _2020_08_13__23_59_59, false, true),
            new SameDayTestCase(QUERY_TIME, _2020_08_14__02_59_59, false, true),
            new SameDayTestCase("1:23", _2020_08_12__03_00_00, false, true),
            new SameDayTestCase("1:23", _2020_08_12__23_59_59, false, true),
            new SameDayTestCase("1:23", _2020_08_13__02_59_59, false, true),

            // Not same-day departures
            new SameDayTestCase(QUERY_TIME, _2020_08_12__23_59_59, false, false),
            new SameDayTestCase(QUERY_TIME, _2020_08_13__02_59_59, false, false),
            new SameDayTestCase(QUERY_TIME, _2020_08_14__03_00_00, false, false),
            new SameDayTestCase("1:23", _2020_08_13__03_00_00, false, false),
            new SameDayTestCase("1:23", _2020_08_13__23_59_59, false, false),
            new SameDayTestCase("1:23", _2020_08_14__02_59_59, false, false),

            // Same-day arrivals
            new SameDayTestCase(QUERY_TIME, _2020_08_13__03_00_00, true, true),
            new SameDayTestCase(QUERY_TIME, _2020_08_13__23_59_59, true, true),
            new SameDayTestCase(QUERY_TIME, _2020_08_14__02_59_59, true, true),
            new SameDayTestCase("1:23", _2020_08_12__03_00_00, true, true),
            new SameDayTestCase("1:23", _2020_08_12__23_59_59, true, true),
            new SameDayTestCase("1:23", _2020_08_13__02_59_59, true, true),

            // Not same-day arrivals
            new SameDayTestCase(QUERY_TIME, _2020_08_12__23_59_59, true, false),
            new SameDayTestCase(QUERY_TIME, _2020_08_13__02_59_59, true, false),
            new SameDayTestCase(QUERY_TIME, _2020_08_14__03_00_00, true, false),
            new SameDayTestCase("1:23", _2020_08_13__03_00_00, true, false),
            new SameDayTestCase("1:23", _2020_08_13__23_59_59, true, false),
            new SameDayTestCase("1:23", _2020_08_14__02_59_59, true, false)
        );
    }

    /**
     * Helper method to create a bare-bones itinerary with start or end time.
     */
    private Itinerary simpleItinerary(Long targetEpochMillis, boolean isArriveBy) {
        Itinerary itinerary = new Itinerary();
        Date date = Date.from(Instant.ofEpochMilli(targetEpochMillis));
        if (isArriveBy) {
            itinerary.endTime = date;
        } else {
            itinerary.startTime = date;
        }
        itinerary.legs = new ArrayList<>();
        return itinerary;
    }

    @ParameterizedTest
    @MethodSource("createDeriveModesFromItineraryTestCases")
    void canDeriveModesFromItinerary(List<String> legModes, List<String> expectedModes, String message) {
        Itinerary itinerary = simpleItinerary(Instant.EPOCH.toEpochMilli(), false);
        itinerary.legs = legModes.stream().map(legMode -> {
            String[] modeParts = legMode.split("_");
            boolean isRent = legMode.endsWith("_RENT");
            Leg leg = new Leg();
            leg.mode = modeParts[0];
            // Field 'rentedbike' includes rented bikes and rented scooters.
            leg.rentedBike = ("BICYCLE".equals(leg.mode) || "SCOOTER".equals(leg.mode)) && isRent;
            leg.rentedCar = "CAR".equals(leg.mode) && isRent;
            leg.hailedCar = "CAR".equals(leg.mode) && legMode.endsWith("_HAIL");
            leg.transitLeg = "BUS".equals(leg.mode);
            return leg;
        }).collect(Collectors.toList());

        Set<OtpGraphQLTransportMode> derivedModes = ItineraryUtils.deriveModesFromItinerary(itinerary);
        assertEquals(expectedModes.size(), derivedModes.size());
        expectedModes.forEach(expMode -> {
            assertEquals(1, derivedModes.stream().filter(m -> m.sameAs(OtpGraphQLTransportMode.fromModeString(expMode))).count(), message);
        });
    }

    private static Stream<Arguments> createDeriveModesFromItineraryTestCases() {
        return Stream.of(
            Arguments.of(List.of("WALK"), List.of("WALK"), "Walk only"),
            Arguments.of(List.of("WALK", "BICYCLE_RENT"), List.of("BICYCLE_RENT"), "Bike rent only"),
            // In OTP2: WALK is implied if a transit mode is also present and can be removed in those cases.
            Arguments.of(List.of("WALK", "BUS"), List.of("BUS"), "Walk + Bus"),
            Arguments.of(List.of("WALK", "BICYCLE", "BUS"), List.of("BICYCLE", "BUS"), "Walk + Bicycle + Bus"),
            Arguments.of(List.of("WALK", "CAR_RENT", "BUS"), List.of("CAR_RENT", "BUS"), "Rented car + Bus"),
            Arguments.of(List.of("WALK", "CAR_PARK", "BUS"), List.of("CAR_PARK", "BUS"), "P+R + Bus"),
            Arguments.of(List.of("WALK", "CAR_HAIL", "BUS"), List.of("CAR_HAIL", "BUS"), "Hail car + Bus")
        );
    }

    /**
     * Data structure for the same-day test.
     */
    private static class SameDayTestCase {
        public final boolean isArriveBy;
        public final boolean shouldBeSameDay;
        public final String timeOfDay;
        public final Long tripTargetTimeEpochMillis;

        public SameDayTestCase(String timeOfDay, Long tripTargetTimeEpochMillis, boolean isArriveBy, boolean shouldBeSameDay) {
            this.isArriveBy = isArriveBy;
            this.shouldBeSameDay = shouldBeSameDay;
            this.timeOfDay = timeOfDay;
            this.tripTargetTimeEpochMillis = tripTargetTimeEpochMillis;
        }

        /**
         * @return A message, in case of test failure, in the form:
         * "On 2020-08-13 at 1:23[America/New_York], a trip arriving at 2020-08-14T02:59:59-04:00[America/New_York] should be considered same-day."
         */
        public String getMessage(ZoneId zoneId) {
            return String.format(
                "On %s at %s[%s], a trip %s at %s %s be considered same-day.",
                QUERY_DATE,
                timeOfDay,
                zoneId.toString(),
                isArriveBy ? "arriving" : "departing",
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(tripTargetTimeEpochMillis), zoneId),
                shouldBeSameDay ? "should" : "should not"
            );
        }
    }
}
