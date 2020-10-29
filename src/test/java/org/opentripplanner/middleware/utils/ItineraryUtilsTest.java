package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcherResponseTest.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest extends OtpMiddlewareTest {
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
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        // Set mocks to a list of responses with itineraries.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp, resp, resp));

        // Also set trip itinerary to the same for easy/lazy match.
        Itinerary expectedItinerary = resp.plan.itineraries.get(0);
        trip.itinerary = expectedItinerary;

        ItineraryExistence result = ItineraryUtils.checkItineraryExistence(trip);//, false);
        Assertions.assertTrue(result.allCheckedDatesAreValid());

        Assertions.assertTrue(result.monday.isValid);
        Assertions.assertEquals(expectedItinerary, result.monday.itinerary);
        Assertions.assertTrue(result.tuesday.isValid);
        Assertions.assertEquals(expectedItinerary, result.tuesday.itinerary);
        Assertions.assertTrue(result.thursday.isValid);
        Assertions.assertEquals(expectedItinerary, result.thursday.itinerary);
        Assertions.assertTrue(result.saturday.isValid);
        Assertions.assertEquals(expectedItinerary, result.saturday.itinerary);
        Assertions.assertTrue(result.sunday.isValid);
        Assertions.assertEquals(expectedItinerary, result.sunday.itinerary);

        Assertions.assertNull(result.wednesday);
        Assertions.assertNull(result.friday);
    }

    /**
     * Test case in which at least one itinerary does not exist,
     * and therefore result.allCheckedDatesAreValid should be false.
     */
    @Test
    public void testAtLeastOneTripDoesNotExist() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = false;
        trip.sunday = false;

        // Set mocks to a list of responses, one without an itinerary.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, otpDispatcherPlanErrorResponse.getResponse()));

        // Also set trip itinerary to the same for easy/lazy match.
        trip.itinerary = resp.plan.itineraries.get(0);

        ItineraryExistence result = ItineraryUtils.checkItineraryExistenceOrdered(trip);//, false);
        Assertions.assertFalse(result.allCheckedDatesAreValid());

        // Assertions ordered by date, Thursday is the query date and therefore comes first.
        Assertions.assertTrue(result.thursday.isValid);
        Assertions.assertTrue(result.monday.isValid);
        Assertions.assertFalse(result.tuesday.isValid);
    }

    /**
     * Check that the query date parameter is properly modified to simulate the given OTP query for different dates.
     */
    @Test
    public void testGetQueriesFromDates() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip();
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        LocalTime queryTime = LocalTime.parse(QUERY_TIME);
        List<String> newDates = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        List<ZonedDateTime> newZonedDateTimes = newDates.stream()
            .map(s -> DateTimeUtils.getDateFromString(s, DEFAULT_DATE_FORMAT_PATTERN))
            .map(d -> ZonedDateTime.of(d, queryTime, DateTimeUtils.getOtpZoneId()))
            .collect(Collectors.toList());

        Map<ZonedDateTime, String> queriesByDate = ItineraryUtils.getQueriesFromDates(trip.parseQueryParams(), newZonedDateTimes);
        Assertions.assertEquals(newDates.size(), queriesByDate.size());

        for (ZonedDateTime zonedDateTime : newZonedDateTimes) {
            MonitoredTrip newTrip = new MonitoredTrip();
            newTrip.queryParams = queriesByDate.get(zonedDateTime);

            Map<String, String> newParams = newTrip.parseQueryParams();
            Assertions.assertEquals(zonedDateTime.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN)),
                newParams.get(DATE_PARAM));
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

        List<ZonedDateTime> datesToCheck = ItineraryUtils.getDatesToCheckItineraryExistence(trip);
        Assertions.assertEquals(testCase.dates, datesToCheck);
    }

    private static List<GetDatesTestCase> createGetDatesTestCases() {
        // Each list includes dates to be monitored in a 7-day window starting from the query date.
        return List.of(
            // Dates solely based on monitored days (see the trip variable in the corresponding test).
            new GetDatesTestCase(List.of(QUERY_DATE /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18"))//,

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

        return trip;
    }

    /**
     * Data structure for the date check test.
     */
    private static class GetDatesTestCase {
        public final List<ZonedDateTime> dates;

        public GetDatesTestCase(List<String> dates) {
            this.dates = dates.stream()
                .map(d -> ZonedDateTime.of(
                    LocalDate.parse(d),
                    LocalTime.parse(QUERY_TIME),
                    DateTimeUtils.getOtpZoneId()
                ))
                .collect(Collectors.toList());
        }
    }
}
