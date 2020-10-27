package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcherResponseTest.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.utils.DateTimeUtils.otpDateTimeAsEpochMillis;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest extends OtpMiddlewareTest {
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
    }

    @AfterEach
    public void tearDownAfterTest() {
        TestUtils.resetOtpMocks();
    }

    /**
     * Test case in which all itineraries exist and result.allItinerariesExist should be true.
     */
    @Test
    public void testAllItinerariesExist() {
        // Set mocks to a list of responses with itineraries.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp));

        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "exist2");
        labeledQueries.put("label3", "exist3");

        ItineraryUtils.Result result = ItineraryUtils.checkItineraryExistence(labeledQueries, false);
        Assertions.assertTrue(result.allItinerariesExist);

        for (String label : labeledQueries.keySet()) {
            Assertions.assertNotNull(result.labeledResponses.get(label));
        }
    }

    /**
     * Test case in which at least one itinerary does not exist,
     * and therefore result.allItinerariesExist should be false.
     */
    @Test
    public void testAtLeastOneTripDoesNotExist() {
        // Set mocks to a list of responses, one without an itinerary.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, otpDispatcherPlanErrorResponse.getResponse(), resp));

        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "not found");
        labeledQueries.put("label3", "exist3");

        ItineraryUtils.Result result = ItineraryUtils.checkItineraryExistence(labeledQueries, false);
        Assertions.assertFalse(result.allItinerariesExist);
    }

    /**
     * Check that the query date parameter is properly modified to simulate the given OTP query for different dates.
     */
    @Test
    public void testGetQueriesFromDates() throws URISyntaxException {
        MonitoredTrip trip = new MonitoredTrip();
        trip.queryParams = BASE_QUERY;

        List<String> newDates = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        Map<String, String> labeledQueries = ItineraryUtils.getQueriesFromDates(trip.parseQueryParams(), newDates);
        Assertions.assertEquals(newDates.size(), labeledQueries.size());

        for (String date : newDates) {
            MonitoredTrip newTrip = new MonitoredTrip();
            newTrip.queryParams = labeledQueries.get(date);

            Map<String, String> newParams = newTrip.parseQueryParams();
            Assertions.assertEquals(date, newParams.get(DATE_PARAM));
        }
    }

    /**
     * Check the computation of the dates corresponding to the monitored days,
     * for which we want to check itinerary existence.
     */
    @ParameterizedTest
    @MethodSource("createGetDatesTestCases")
    public void testGetDatesToCheckItineraryExistence(GetDatesTestCase testCase) throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        List<String> checkedDates = ItineraryUtils.getDatesToCheckItineraryExistence(trip, testCase.checkAllDays);
        Assertions.assertEquals(testCase.dates, checkedDates);
    }

    private static List<GetDatesTestCase> createGetDatesTestCases() {
        // Each list includes dates to be monitored in a 7-day window starting from the query date.
        return List.of(
            // Dates solely based on monitored days (see the trip variable in the corresponding test).
            new GetDatesTestCase(false, List.of(QUERY_DATE /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18")),

            // If we forceAllDays to ItineraryUtils.getDatesToCheckItineraryExistence,
            // it should return all dates in the 7-day window regardless of the ones set in the monitored trip.
            new GetDatesTestCase(true, List.of(QUERY_DATE /* Thursday */, "2020-08-14", "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18", "2020-08-19"))
        );
    }

    /**
     * Check that the ignoreRealtime query parameter is set to true
     * regardless of whether it was originally missing or false.
     */
    @Test
    public void testAddIgnoreRealtimeParam() throws URISyntaxException {
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
     * Check that a given trip is modified with an OTP itinerary that exists,
     * right now based on the index of the selected itinerary in the UI.
     */
    @Test
    public void testUpdateTripWithVerifiedItinerary() throws URISyntaxException {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "testUpdateTripWithVerifiedItinerary";
        trip.queryParams = BASE_QUERY + "&ui_activeItinerary=1";
        trip.itinerary = null;

        List<Itinerary> itineraries = otpDispatcherPlanResponse.getResponse().plan.itineraries;

        ItineraryUtils.updateTripWithVerifiedItinerary(trip, itineraries);

        Assertions.assertEquals(itineraries.get(1), trip.itinerary);
    }

    @ParameterizedTest
    @MethodSource("createSameDayTestCases")
    void testIsSameDay(SameDayTestCase testCase) {
        // The time zone for trip is OTP's time zone.
        ZoneId zoneId = DateTimeUtils.getOtpZoneId();

        Itinerary itinerary = simpleItinerary(testCase.tripTargetTimeEpochMillis, testCase.isArriveBy);
        Assertions.assertEquals(
            testCase.shouldBeSameDay,
            ItineraryUtils.isSameDay(itinerary, QUERY_DATE, testCase.timeOfDay, zoneId, testCase.isArriveBy),
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
     * Check that only same-day itineraries are selected.
     */
    @Test
    public void testGetSameDayItineraries() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        trip.tripTime = QUERY_TIME;

        List<Long> startTimes = List.of(
            _2020_08_13__23_59_59, // same day
            _2020_08_14__02_59_59, // considered same day
            _2020_08_14__03_00_00 // not same day
        );

        // Create itineraries, some being same-day, some not per the times above.
        List<Itinerary> itineraries = startTimes.stream()
            .map(t -> simpleItinerary(t, false))
            .collect(Collectors.toList());

        List<Itinerary> processedItineraries = ItineraryUtils.getSameDayItineraries(itineraries, trip, QUERY_DATE);
        Assertions.assertEquals(2, processedItineraries.size());
        Assertions.assertTrue(processedItineraries.contains(itineraries.get(0)));
        Assertions.assertTrue(processedItineraries.contains(itineraries.get(1)));
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

        trip.queryParams = BASE_QUERY;

        return trip;
    }

    /**
     * Data structure for the date check test.
     */
    private static class GetDatesTestCase {
        public final boolean checkAllDays;
        public final List<String> dates;

        public GetDatesTestCase(boolean checkAllDays, List<String> dates) {
            this.checkAllDays = checkAllDays;
            this.dates = dates;
        }
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
