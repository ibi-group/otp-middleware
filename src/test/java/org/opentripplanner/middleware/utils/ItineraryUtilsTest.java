package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
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

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcherResponseTest.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.DateTimeUtils.otpDateTimeAsEpochMillis;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryUtilsTest.class);
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    // Date and time from the above query.
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

    // Timestamps (in OTP's timezone) to test whether an itinerary is same-day as QUERY_DATE.
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

    private static OtpDispatcherResponse otpDispatcherPlanResponse;
    private static OtpDispatcherResponse otpDispatcherPlanErrorResponse;
    private static Itinerary defaultItinerary;

    @BeforeAll
    public static void setup() throws IOException {
        TestUtils.mockOtpServer();

        // Contains an OTP response with an itinerary found.
        // (We are reusing an existing response. The exact contents of the response does not matter
        // for the purposes of this class.)
        String mockPlanResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        // Contains an OTP response with no itinerary found.
        String mockErrorResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planErrorResponse.json"
        );

        otpDispatcherPlanResponse = new OtpDispatcherResponse(mockPlanResponse, DEFAULT_PLAN_URI);
        otpDispatcherPlanErrorResponse = new OtpDispatcherResponse(mockErrorResponse, DEFAULT_PLAN_URI);
        defaultItinerary = otpDispatcherPlanResponse.getResponse().plan.itineraries.get(0);
    }

    @AfterEach
    public void tearDownAfterTest() {
        TestUtils.resetOtpMocks();
    }

    /**
     * Test case in which all itineraries exist and result.allCheckedDatesAreValid should be true.
     */
    @Test
    public void canCheckAllItinerariesExist() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();

        // Set mocks to a list of responses with itineraries.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp, resp, resp));

        // Also set trip itinerary to the same for easy/lazy match.
        Itinerary expectedItinerary = resp.plan.itineraries.get(0);
        trip.itinerary = expectedItinerary;

        trip.checkItineraryExistence(false, false);
        ItineraryExistence existence = trip.itineraryExistence;

        Assertions.assertTrue(existence.allCheckedDaysAreValid());
        // FIXME: For now, just check that the first itinerary in the list is valid. If we expand our check window from
        //  7 days to 14 (or more) days, this may need to be adjusted.
        Assertions.assertTrue(existence.monday.isValid());
        Assertions.assertTrue(ItineraryUtils.itinerariesMatch(expectedItinerary, existence.monday.itineraries.get(0)));
        Assertions.assertTrue(existence.tuesday.isValid());
        Assertions.assertTrue(ItineraryUtils.itinerariesMatch(expectedItinerary, existence.tuesday.itineraries.get(0)));
        Assertions.assertTrue(existence.thursday.isValid());
        Assertions.assertTrue(
            ItineraryUtils.itinerariesMatch(expectedItinerary, existence.thursday.itineraries.get(0))
        );
        Assertions.assertTrue(existence.saturday.isValid());
        Assertions.assertTrue(
            ItineraryUtils.itinerariesMatch(expectedItinerary, existence.saturday.itineraries.get(0))
        );
        Assertions.assertTrue(existence.sunday.isValid());
        Assertions.assertTrue(ItineraryUtils.itinerariesMatch(expectedItinerary, existence.sunday.itineraries.get(0)));

        Assertions.assertNull(existence.wednesday);
        Assertions.assertNull(existence.friday);
    }

    /**
     * Test case in which at least one itinerary does not exist,
     * and therefore result.allCheckedDatesAreValid should be false.
     */
    @Test
    public void canCheckAtLeastOneTripDoesNotExist() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();

        // Set mocks to a list of responses, one without an itinerary.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp, otpDispatcherPlanErrorResponse.getResponse(), resp));

        // Also set trip itinerary to the same for easy/lazy match.
        trip.itinerary = resp.plan.itineraries.get(0);

        // Sort dates to ensure OTP responses match the dates.
        trip.checkItineraryExistence(false, false);
        Assertions.assertFalse(trip.itineraryExistence.allCheckedDaysAreValid());

        // Assertions ordered by date, Thursday is the query date and therefore comes first.
        Assertions.assertTrue(trip.itineraryExistence.thursday.isValid());
        Assertions.assertTrue(trip.itineraryExistence.saturday.isValid());
        Assertions.assertTrue(trip.itineraryExistence.sunday.isValid());
        Assertions.assertFalse(trip.itineraryExistence.monday.isValid());
        Assertions.assertTrue(trip.itineraryExistence.tuesday.isValid());
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
                testDate.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN)),
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
                List.of(QUERY_DATE /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18")
            ), false)

            // If we forceAllDays to ItineraryUtils.getDatesToCheckItineraryExistence,
            // it should return all dates in the 7-day window regardless of the ones set in the monitored trip.
            //new GetDatesTestCase(List.of(QUERY_DATE /* Thursday */, "2020-08-14", "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18", "2020-08-19"))

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
                defaultItinerary.clone(),
                true
            )
        );

        // should not be equal with a different amount of legs
        Leg extraBikeLeg = new Leg();
        extraBikeLeg.mode = "BICYCLE";
        Itinerary itineraryWithMoreLegs = defaultItinerary.clone();
        itineraryWithMoreLegs.legs.add(extraBikeLeg);
        testCases.add(
            new ItineraryMatchTestCase(
                "should not be equal with a different amount of legs",
                itineraryWithMoreLegs,
                false
            )
        );

        // should be equal with realtime data on transit leg (same day)
        Itinerary itineraryWithRealtimeTransit = defaultItinerary.clone();
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
        Itinerary itineraryOnFutureDate = defaultItinerary.clone();
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
                this.previousItinerary = defaultItinerary;
            }
            this.newItinerary = newItinerary;
            this.shouldMatch = shouldMatch;
        }
    }

    @ParameterizedTest
    @MethodSource("createSameDayTestCases")
    void testIsSameDay(SameDayTestCase testCase) {
        // The time zone for trip is OTP's time zone.
        ZoneId zoneId = DateTimeUtils.getOtpZoneId();

        Itinerary itinerary = simpleItinerary(testCase.tripTargetTimeEpochMillis, testCase.isArriveBy);

        ZonedDateTime queryDateTime = ZonedDateTime.of(
          LocalDate.parse(QUERY_DATE, DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN)),
            LocalTime.parse(testCase.timeOfDay, DateTimeFormatter.ofPattern("H:mm")),
            DateTimeUtils.getOtpZoneId()
        );

        Assertions.assertEquals(
            testCase.shouldBeSameDay,
            ItineraryUtils.isSameDay(itinerary, queryDateTime, testCase.isArriveBy),
            testCase.getMessage(zoneId)
        );
    }

    private static List<SameDayTestCase> createSameDayTestCases() {
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
