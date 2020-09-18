package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest {
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";
    public static final String QUERY_DATE = "2020-08-13";

    @Test
    public void testGetQueriesFromDates() throws URISyntaxException {
        // Abbreviated query for this test.
        List<String> newDates = List.of("2020-12-30", "2020-12-31", "2021-01-01");
        Map<String, String> labeledQueries = ItineraryUtils.getQueriesFromDates(BASE_QUERY, newDates);
        Assertions.assertEquals(newDates.size(), labeledQueries.size());

        for (String date : newDates) {
            // Insert a '?' in order to parse.
            Map<String, String> newParams = ItineraryUtils.getQueryParams("?" + labeledQueries.get(date));
            Assertions.assertEquals(date, newParams.get(DATE_PARAM));
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
        List<String> newDates = List.of(QUERY_DATE /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18");
        List<String> checkedDates = ItineraryUtils.getDatesToCheckItineraryExistence(trip);
        Assertions.assertEquals(newDates, checkedDates);
    }

    @Test
    public void testAddIgnoreRealtimeParam() throws URISyntaxException {
        String queryWithRealtimeParam = BASE_QUERY + "&" + IGNORE_REALTIME_UPDATES_PARAM + "=false";
        List<String> queries = List.of(BASE_QUERY, queryWithRealtimeParam);

        for (String query : queries) {
            String queryExcludingRealtime = ItineraryUtils.excludeRealtime(query);
            Map<String, String> params = ItineraryUtils.getQueryParams(queryExcludingRealtime);
            Assertions.assertEquals("true", params.get(IGNORE_REALTIME_UPDATES_PARAM));
        }
    }

    @Test
    public void testUpdateTripWithVerifiedItinerary() throws IOException, URISyntaxException {
        String query = BASE_QUERY + "&ui_activeItinerary=1";

        String mockResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse(mockResponse);
        List<Itinerary> itineraries = otpDispatcherResponse.getResponse().plan.itineraries;

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "testUpdateTripWithVerifiedItinerary";
        trip.queryParams = query;
        // Write some itinerary that is not the one we want.
        trip.itinerary = itineraries.get(0);

        ItineraryUtils.updateTripWithVerifiedItinerary(trip, itineraries);

        Assertions.assertEquals(itineraries.get(1), trip.itinerary);
    }

    private void testItineraryDepartsSameDay(boolean expected, Long... startTimes) {
        // Trip is in US Eastern timezone per the place set in makeBarebonesTrip().
        MonitoredTrip trip = makeBarebonesTrip();
        ZoneId zoneId = trip.tripZoneId();

        // startTimes are in US Eastern timezone.
        for (Long startTime : startTimes) {
            Itinerary itinerary = new Itinerary();
            Instant instant = Instant.ofEpochMilli(startTime);
            itinerary.startTime = Date.from(instant);
            Assertions.assertEquals(
                expected,
                ItineraryUtils.itineraryDepartsSameDay(itinerary, QUERY_DATE, zoneId),
                String.format(
                    "%s %s be considered same day as %s",
                    ZonedDateTime.ofInstant(instant, zoneId),
                    expected ? "should" : "should not",
                    QUERY_DATE
                )
            );
        }
    }

    @Test
    public void testItineraryDepartsSameDay() {
        // All times EDT (GMT-04:00)
        testItineraryDepartsSameDay(true,
            1597302000000L, // August 13, 2020 3:00:00 AM
            1597377599000L, // August 13, 2020 11:59:59 PM
            1597388399000L // August 14, 2020 02:59:59 AM, considered to be Aug 13.
        );
    }

    @Test
    public void testItineraryDoesNotDepartSameDay() {
        // All times EDT (GMT-04:00)
        testItineraryDepartsSameDay(false,
            1597291199000L, // August 12, 2020 11:59:59 PM
            1597291200000L, // August 13, 2020 2:59:59 AM
            1597388400000L // August 14 2020 3:00:00 AM
        );
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
