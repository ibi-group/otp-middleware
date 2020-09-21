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

    // Date and time from the above query.
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

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
        MonitoredTrip trip = makeTestTrip(false);
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

    private void testIsSameDay(String time, boolean isArrival, boolean expected, Long... startTimes) throws URISyntaxException {
        // Trip arrives in US Eastern timezone per the place set in makeTestTrip().
        MonitoredTrip trip = makeTestTrip(isArrival);
        ZoneId zoneId = trip.timezoneForTargetLocation();

        // startTimes are in US Eastern timezone.
        for (Long startTime : startTimes) {
            Itinerary itinerary = new Itinerary();
            Instant instant = Instant.ofEpochMilli(startTime);
            itinerary.setStartOrEndTime(Date.from(instant), isArrival);
            Assertions.assertEquals(
                expected,
                ItineraryUtils.isSameDay(itinerary, QUERY_DATE, time, zoneId, isArrival),
                String.format(
                    "%s %s be considered same day as %s %s",
                    ZonedDateTime.ofInstant(instant, zoneId),
                    expected ? "should" : "should not",
                    QUERY_DATE,
                    time
                )
            );
        }
    }

    @Test
    public void testItineraryDepartsSameDay() throws URISyntaxException {
        // All times EDT (GMT-04:00)
        testIsSameDay(QUERY_TIME, false, true,
            1597302000000L, // August 13, 2020 3:00:00 AM
            1597377599000L, // August 13, 2020 11:59:59 PM
            1597388399000L // August 14, 2020 02:59:59 AM, considered to be Aug 13.
        );
        testIsSameDay("1:23", false, true,
            1597215600000L, // August 12, 2020 3:00:00 AM
            1597291199000L, // August 12, 2020 11:59:59 PM
            1597301999000L // August 13, 2020 02:59:59 AM, considered to be Aug 12.
        );
    }

    @Test
    public void testItineraryDoesNotDepartSameDay() throws URISyntaxException {
        // All times EDT (GMT-04:00)
        testIsSameDay(QUERY_TIME, false, false,
            1597291199000L, // August 12, 2020 11:59:59 PM
            1597291200000L, // August 13, 2020 2:59:59 AM
            1597388400000L // August 14 2020 3:00:00 AM
        );
        testIsSameDay("1:23", false, false,
            1597302000000L, // August 13, 2020 3:00:00 AM
            1597377599000L, // August 13, 2020 11:59:59 PM
            1597388399000L // August 14, 2020 02:59:59 AM, considered to be Aug 13.
        );
    }

    @Test
    public void testItineraryArrivesSameDay() throws URISyntaxException {
        // All times EDT (GMT-04:00)
        testIsSameDay(QUERY_TIME, true, true,
            1597302000000L, // August 13, 2020 3:00:00 AM
            1597377599000L, // August 13, 2020 11:59:59 PM
            1597388399000L // August 14, 2020 02:59:59 AM, considered to be Aug 13.
        );
        testIsSameDay("1:23", true, true,
            1597215600000L, // August 12, 2020 3:00:00 AM
            1597291199000L, // August 12, 2020 11:59:59 PM
            1597301999000L // August 13, 2020 02:59:59 AM, considered to be Aug 12.
        );
    }

    @Test
    public void testItineraryDoesNotArriveSameDay() throws URISyntaxException {
        // All times EDT (GMT-04:00)
        testIsSameDay(QUERY_TIME, true, false,
            1597291199000L, // August 12, 2020 11:59:59 PM
            1597291200000L, // August 13, 2020 2:59:59 AM
            1597388400000L // August 14 2020 3:00:00 AM
        );
        testIsSameDay("1:23", true, false,
            1597302000000L, // August 13, 2020 3:00:00 AM
            1597377599000L, // August 13, 2020 11:59:59 PM
            1597388399000L // August 14, 2020 02:59:59 AM, considered to be Aug 13.
        );
    }

    private MonitoredTrip makeTestTrip(boolean arriveBy) throws URISyntaxException {
        Place targetPlace = new Place();
        targetPlace.lat = 33.80;
        targetPlace.lon = -84.70; // America/NewYork

        Place dummyPlace = new Place();
        dummyPlace.lat = 33.90;
        dummyPlace.lon = 0.0; // Africa/Algiers.

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "Test trip";

        if (arriveBy) {
            trip.from = dummyPlace;
            trip.to = targetPlace;

            Map<String, String> baseQueryParams = ItineraryUtils.getQueryParams(BASE_QUERY);
            baseQueryParams.put("arriveBy", "true");
            trip.queryParams = ItineraryUtils.toQueryString(baseQueryParams, true);
        } else { // departBy
            trip.from = targetPlace;
            trip.to = dummyPlace;

            trip.queryParams = BASE_QUERY;
        }
        return trip;
    }
}
