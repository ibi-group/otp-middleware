package org.opentripplanner.middleware.utils;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opentripplanner.middleware.testutils.CommonTestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.utils.DateTimeUtils.otpDateTimeAsEpochMillis;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryUtilsTest.class);
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    /** Date and time from the above query. */
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

    public static final List<String> MONITORED_TRIP_DATES = List.of(
        QUERY_DATE, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18"
    );

    /** Timestamps (in OTP's timezone) to test whether an itinerary is same-day as QUERY_DATE. */
    public static final long _2020_08_12__03_00_00 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 12, 3, 0, 0)); // Aug 12, 2020 3:00:00 AM
    public static final long _2020_08_12__23_59_59 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020,8,12,23,59,59)); // Aug 12, 2020 11:59:59 PM
    public static final long _2020_08_13__02_59_59 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 13, 2, 59, 59)); // Aug 13, 2020 2:59:59 AM, considered to be Aug 12.
    public static final long _2020_08_13__03_00_00 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 13, 3, 0, 0)); // Aug 13, 2020 3:00:00 AM
    public static final long _2020_08_13__23_59_59 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 13, 23, 59, 59)); // Aug 13, 2020 11:59:59 PM
    public static final long _2020_08_14__02_59_59 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 14, 2, 59, 59)); // Aug 14, 2020 2:59:59 AM, considered to be Aug 13.
    public static final long _2020_08_14__03_00_00 = otpDateTimeAsEpochMillis(LocalDateTime.of(
        2020, 8, 14, 3, 0, 0)); // Aug 14, 2020 3:00:00 AM

    /** Contains an OTP response with an itinerary. */
    private static final OtpDispatcherResponse OTP_DISPATCHER_PLAN_RESPONSE =
        initializeMockPlanResponse(TEST_RESOURCE_PATH + "persistence/planResponse.json");
    /** Contains an OTP response with no itinerary found. */
    private static final OtpDispatcherResponse OTP_DISPATCHER_PLAN_ERROR_RESPONSE =
        initializeMockPlanResponse(TEST_RESOURCE_PATH + "persistence/planErrorResponse.json");
    /** Contains the verified itinerary set for a trip upon persisting. */
    private static final Itinerary DEFAULT_ITINERARY = OTP_DISPATCHER_PLAN_RESPONSE.getResponse().plan.itineraries.get(0);
    public static final ZoneId OTP_ZONE_ID = DateTimeUtils.getOtpZoneId();

    @BeforeAll
    public static void setup() throws IOException {
        OtpTestUtils.mockOtpServer();
    }

    public static OtpDispatcherResponse initializeMockPlanResponse(String path) {
        // Contains an OTP response with an itinerary found.
        // (We are reusing an existing response. The exact contents of the response does not matter
        // for the purposes of this class.)
        String mockPlanResponse = null;
        try {
            mockPlanResponse = FileUtils.getFileContents(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new OtpDispatcherResponse(mockPlanResponse, DEFAULT_PLAN_URI);
    }

    @AfterEach
    public void tearDownAfterTest() {
        OtpTestUtils.resetOtpMocks();
    }

    /**
     * Test that itineraries exist and result.allCheckedDatesAreValid are as expected.
     */
    @ParameterizedTest
    @MethodSource("createCheckAllItinerariesExistTestCases")
    public void canCheckAllItinerariesExist(boolean insertInvalidDay, String message) throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        List<OtpResponse> mockOtpResponses = getMockDatedOtpResponses(MONITORED_TRIP_DATES);

        // If needed, insert a mock invalid response for one of the monitored days.
        final int INVALID_DAY_INDEX = 3;
        if (insertInvalidDay) {
            mockOtpResponses.set(INVALID_DAY_INDEX, OTP_DISPATCHER_PLAN_ERROR_RESPONSE.getResponse());
        }

        OtpTestUtils.setupOtpMocks(mockOtpResponses);

        // Also set trip itinerary to the template itinerary for easy/lazy match.
        Itinerary expectedItinerary = mockOtpResponses.get(0).plan.itineraries.get(0);
        trip.itinerary = expectedItinerary;

        trip.checkItineraryExistence(false, false);
        ItineraryExistence existence = trip.itineraryExistence;

        boolean allDaysValid = !insertInvalidDay;
        Assertions.assertEquals(allDaysValid, existence.allCheckedDaysAreValid(), message);

        // Valid days, in the order of the OTP requests sent (so we can track the invalid entry if we inserted one).
        ArrayList<ItineraryExistence.ItineraryExistenceResult> validDays = Lists.newArrayList(
            existence.thursday,
            existence.saturday,
            existence.sunday,
            existence.monday,
            existence.tuesday
        );
        // Check (and remove) the extra invalid day if we inserted one above.
        if (insertInvalidDay) {
            Assertions.assertFalse(validDays.get(INVALID_DAY_INDEX).isValid());
            validDays.remove(INVALID_DAY_INDEX);
        }

        // FIXME: For now, just check that the first itinerary in the list is valid. If we expand our check window from
        //  7 days to 14 (or more) days, this may need to be adjusted.
        for (ItineraryExistence.ItineraryExistenceResult validDay : validDays) {
            Assertions.assertTrue(validDay.isValid());
            Assertions.assertTrue(ItineraryUtils.itinerariesMatch(expectedItinerary, validDay.itineraries.get(0)));
        }

        // When the check is not requested for a day, the existence entry will be null.
        Assertions.assertNull(existence.wednesday);
        Assertions.assertNull(existence.friday);
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
    public static List<OtpResponse> getMockDatedOtpResponses(List<String> dates) {
        // Set mocks to a list of responses with itineraries, ordered by day.
        List<OtpResponse> mockOtpResponses = new ArrayList<>();

        for (String dateString : dates) {
            LocalDate monitoredDate = LocalDate.parse(dateString, DateTimeUtils.DEFAULT_DATE_FORMATTER);

            // Copy the template OTP response itinerary, and change the itinerary date to the monitored date,
            // in order to pass the same-day itinerary requirement.
            OtpResponse resp = OTP_DISPATCHER_PLAN_RESPONSE.getResponse();
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
    public void canGetQueriesFromDates() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        // Create test dates.
        List<String> testDateStrings = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        LOG.info(String.join(", ", testDateStrings));
        List<ZonedDateTime> testDates = datesToZonedDateTimes(testDateStrings);
        // Get OTP requests modified with dates.
        List<OtpRequest> requests = ItineraryUtils.getOtpRequestsForDates(trip.parseQueryParams(), testDates);
        Assertions.assertEquals(testDateStrings.size(), requests.size());
        // Iterate over OTP requests and verify that query dates match the input.
        for (int i = 0; i < testDates.size(); i++) {
            ZonedDateTime testDate = testDates.get(i);
            Map<String, String> newParams = requests.get(i).requestParameters;
            Assertions.assertEquals(
                testDate.format(DateTimeUtils.DEFAULT_DATE_FORMATTER),
                newParams.get(DATE_PARAM)
            );
        }
    }

    /**
     * Check the computation of the dates corresponding to the monitored days,
     * for which we want to check itinerary existence.
     */
    @ParameterizedTest
    @MethodSource("createGetDatesTestCases")
    public void canGetDatesToCheckItineraryExistence(List<ZonedDateTime> testDates, boolean checkAllDays) throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        List<ZonedDateTime> datesToCheck = ItineraryUtils.getDatesToCheckItineraryExistence(trip, checkAllDays);
        Assertions.assertEquals(testDates, datesToCheck);
    }

    private static Stream<Arguments> createGetDatesTestCases() {
        // Each list includes dates to be monitored in a 7-day window starting from the query date.
        return Stream.of(
            // Dates solely based on monitored days (see the trip variable in the corresponding test).
            Arguments.of(datesToZonedDateTimes(
                MONITORED_TRIP_DATES
                ), false),

            // If we forceAllDays to ItineraryUtils.getDatesToCheckItineraryExistence,
            // it should return all dates in the 7-day window regardless of the ones set in the monitored trip.
            Arguments.of(datesToZonedDateTimes(List.of(
                QUERY_DATE /* Thursday */, "2020-08-14", "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18", "2020-08-19")
            ), true)
        );
    }

    /**
     * Check that the ignoreRealtime query parameter is set to true
     * regardless of whether it was originally missing or false.
     */
    @Test
    public void canAddIgnoreRealtimeParam() throws URISyntaxException {
        String queryWithRealtimeParam = BASE_QUERY + "&" + IGNORE_REALTIME_UPDATES_PARAM + "=false";
        List<String> queries = List.of(BASE_QUERY, queryWithRealtimeParam);

        for (String query : queries) {
            MonitoredTrip trip = new MonitoredTrip();
            trip.queryParams = query;
            Map<String, String> params = ItineraryUtils.excludeRealtime(trip.parseQueryParams());
            Assertions.assertEquals("true", params.get(IGNORE_REALTIME_UPDATES_PARAM));
        }
    }

    /**
     * Check whether certain itineraries match.
     */
    @ParameterizedTest
    @MethodSource("createItineraryComparisonTestCases")
    public void testItineraryMatches(ItineraryMatchTestCase testCase) {
        Assertions.assertEquals(
            testCase.shouldMatch,
            ItineraryUtils.itinerariesMatch(testCase.previousItinerary, testCase.newItinerary),
            testCase.name
        );
    }

    private static List<ItineraryMatchTestCase> createItineraryComparisonTestCases() throws CloneNotSupportedException {
        List<ItineraryMatchTestCase> testCases = new ArrayList<>();

        // should match same data
        testCases.add(
            new ItineraryMatchTestCase(
                "Should be equal with same data",
                DEFAULT_ITINERARY.clone(),
                true
            )
        );

        // should not be equal with a different amount of legs
        Leg extraBikeLeg = new Leg();
        extraBikeLeg.mode = "BICYCLE";
        Itinerary itineraryWithMoreLegs = DEFAULT_ITINERARY.clone();
        itineraryWithMoreLegs.legs.add(extraBikeLeg);
        testCases.add(
            new ItineraryMatchTestCase(
                "should not be equal with a different amount of legs",
                itineraryWithMoreLegs,
                false
            )
        );

        // should be equal with realtime data on transit leg (same day)
        Itinerary itineraryWithRealtimeTransit = DEFAULT_ITINERARY.clone();
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
        Itinerary itineraryOnFutureDate = DEFAULT_ITINERARY.clone();
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
            .map(d -> DateTimeUtils.makeZonedDateTime(d, QUERY_TIME))
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
        ) {
            this(name, null, newItinerary, shouldMatch);
        }

        public ItineraryMatchTestCase(
            String name,
            Itinerary previousItinerary,
            Itinerary newItinerary,
            boolean shouldMatch
        ) {
            this.name = name;
            if (previousItinerary != null) {
                this.previousItinerary = previousItinerary;
            } else {
                this.previousItinerary = DEFAULT_ITINERARY;
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
            OTP_ZONE_ID
        );

        Assertions.assertEquals(
            testCase.shouldBeSameDay,
            ItineraryUtils.occursOnSameServiceDay(itinerary, queryDateTime, testCase.isArriveBy),
            testCase.getMessage(OTP_ZONE_ID)
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
