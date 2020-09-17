package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest {
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    @Test
    public void testQueriesFromDates() throws URISyntaxException {
        // Abbreviated query for this test.
        List<String> newDates = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        Map<String, String> baseParams = ItineraryUtils.getQueryParams("?" + BASE_QUERY);

        List<String> result = ItineraryUtils.queriesFromDates(BASE_QUERY, newDates);
        Assertions.assertEquals(newDates.size(), result.size());
        for (int i = 0; i < newDates.size(); i++) {
            // Insert a '?' in order to parse.
            Map<String, String> newParams = ItineraryUtils.getQueryParams("?" + result.get(i));
            Assertions.assertEquals(newDates.get(i), newParams.get(DATE_PARAM));
            // Also check other params are present (BASE_QUERY already contains the date param).
            Assertions.assertEquals(baseParams.size(), newParams.size());
        }
    }

    @Test
    public void testGetDatesToCheckItineraryExistence() throws URISyntaxException {
        MonitoredTrip trip = makeBarebonesTrip();
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        // The list includes dates to be monitored in a 7-day window starting from the query date.
        List<String> newDates = List.of("2020-08-13" /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18");
        List<String> checkedDates = ItineraryUtils.getDatesToCheckItineraryExistence(trip);
        Assertions.assertEquals(newDates, checkedDates);
    }

    @Test
    public void testAddIgnoreRealtimeParam() throws URISyntaxException {
        String queryWithRealtimeParam = BASE_QUERY + "&" + IGNORE_REALTIME_UPDATES_PARAM + "=false";
        List<String> queries = List.of(BASE_QUERY, queryWithRealtimeParam);

        for (String query : queries) {
            String result = ItineraryUtils.excludeRealtime(query);
            Map<String, String> params = ItineraryUtils.getQueryParams(result);
            Assertions.assertEquals("true", params.get(IGNORE_REALTIME_UPDATES_PARAM));
        }
    }

    @Test
    public void testWriteVerifiedNonRealTimeItinerary() throws IOException, URISyntaxException {
        String query = BASE_QUERY + "&ui_activeItinerary=1";

        String mockResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse(mockResponse);
        List<Itinerary> itineraries = otpDispatcherResponse.getResponse().plan.itineraries;

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "testWriteVerifiedNonRealTimeItinerary";
        trip.queryParams = query;
        // Write some itinerary that is not the one we want.
        trip.itinerary = itineraries.get(0);

        ItineraryUtils.writeVerifiedItinerary(trip, itineraries);

        Assertions.assertEquals(itineraries.get(1), trip.itinerary);
    }

    @Test
    public void testGetResponseForDate() {
        String desiredDate = "2020-09-01";
        List<String> dates = List.of("2020-08-12", "2021-02-10", "2020-09-01");

        List<Response> responses = new ArrayList<>();
        for (String date : dates) {
            Response response = new Response();
            response.requestParameters = new HashMap<>();
            response.requestParameters.put(DATE_PARAM, date);
            responses.add(response);
        }

        Assertions.assertEquals(responses.get(2), ItineraryUtils.getResponseForDate(responses, desiredDate));
    }

    @Test
    public void testItineraryDepartsSameDay() {
        // Trip is in US Eastern timezone.
        MonitoredTrip trip = makeBarebonesTrip();

        // August 13, 2020 12:00:00 AM and 11:59:59 PM EDT (GMT-04:00)
        List<Long> startTimes = List.of(1597291200000L, 1597377599000L);
        for (Long startTime : startTimes) {
            Itinerary itinerary = new Itinerary();
            itinerary.startTime = Date.from(Instant.ofEpochMilli(startTime));
            Assertions.assertTrue(ItineraryUtils.itineraryDepartsSameDay(itinerary, "2020-08-13", trip.tripZoneId()));
        }
    }

    @Test
    public void testItineraryDoesNotDepartSameDay() {
        // Trip is in US Eastern timezone.
        MonitoredTrip trip = makeBarebonesTrip();

        // August 12, 2020 11:59:59 PM EDT, August 14 2020 3:01 AM EDT
        List<Long> startTimes = List.of(1597291199000L, 1597388460000L);
        for (Long startTime : startTimes) {
            Itinerary itinerary = new Itinerary();
            itinerary.startTime = Date.from(Instant.ofEpochMilli(startTime));
            Assertions.assertFalse(ItineraryUtils.itineraryDepartsSameDay(itinerary, "2020-08-13", trip.tripZoneId()));
        }
    }

    private MonitoredTrip makeBarebonesTrip() {
        Place fromPlace = new Place();
        fromPlace.lat = 33.80;
        fromPlace.lon = -84.70;

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "Test trip";
        trip.from = fromPlace;
        trip.queryParams = BASE_QUERY;
        return trip;
    }
}
