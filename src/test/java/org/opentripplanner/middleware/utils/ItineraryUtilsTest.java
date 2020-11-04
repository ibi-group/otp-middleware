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
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcherResponseTest.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryUtilsTest.class);
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    // Date and time from the above query.
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

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
     * Test case in which all itineraries exist and result.allCheckedDatesAreValid should be true.
     */
    @Test
    public void testAllItinerariesExist() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();

        // Set mocks to a list of responses with itineraries.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp, resp, resp));

        // Also set trip itinerary to the same for easy/lazy match.
        Itinerary expectedItinerary = resp.plan.itineraries.get(0);
        trip.itinerary = expectedItinerary;

        trip.checkItineraryExistence(false, false);
        Assertions.assertTrue(trip.itineraryExistence.allCheckedDaysAreValid());
        // FIXME: For now, just check that the first itinerary in the list is valid. If we expand our check window from
        //  7 days to 14 (or more) days, this may need to be adjusted.
        Assertions.assertTrue(trip.itineraryExistence.monday.isValid());
        Assertions.assertEquals(expectedItinerary, trip.itineraryExistence.monday.itineraries.get(0));
        Assertions.assertTrue(trip.itineraryExistence.tuesday.isValid());
        Assertions.assertEquals(expectedItinerary, trip.itineraryExistence.tuesday.itineraries.get(0));
        Assertions.assertTrue(trip.itineraryExistence.thursday.isValid());
        Assertions.assertEquals(expectedItinerary, trip.itineraryExistence.thursday.itineraries.get(0));
        Assertions.assertTrue(trip.itineraryExistence.saturday.isValid());
        Assertions.assertEquals(expectedItinerary, trip.itineraryExistence.saturday.itineraries.get(0));
        Assertions.assertTrue(trip.itineraryExistence.sunday.isValid());
        Assertions.assertEquals(expectedItinerary, trip.itineraryExistence.sunday.itineraries.get(0));

        Assertions.assertNull(trip.itineraryExistence.wednesday);
        Assertions.assertNull(trip.itineraryExistence.friday);
    }

    /**
     * Test case in which at least one itinerary does not exist,
     * and therefore result.allCheckedDatesAreValid should be false.
     */
    @Test
    public void testAtLeastOneTripDoesNotExist() throws URISyntaxException {
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
    public void testGetQueriesFromDates() throws URISyntaxException {
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
    public void testGetDatesToCheckItineraryExistence(List<ZonedDateTime> testDates, boolean checkAllDays) throws URISyntaxException {
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
}
