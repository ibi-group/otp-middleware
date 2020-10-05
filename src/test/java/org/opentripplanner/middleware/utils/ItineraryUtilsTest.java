package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.IGNORE_REALTIME_UPDATES_PARAM;

public class ItineraryUtilsTest {
    /** Abbreviated query for the tests */
    public static final String BASE_QUERY = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

    // Date and time from the above query.
    public static final String QUERY_DATE = "2020-08-13";
    public static final String QUERY_TIME = "11:23";

    // Timestamps (in EDT or GMT-4:00) to test whether an itinerary is same-day as QUERY_DATE.
    // Note that the timezone matches the location time zone from makeTestTrip.
    public static final long _2020_08_12__03_00_00_EDT = 1597215600000L; // Aug 12, 2020 3:00:00 AM
    public static final long _2020_08_12__23_59_59_EDT = 1597291199000L; // Aug 12, 2020 11:59:59 PM
    public static final long _2020_08_13__02_59_59_EDT = 1597301999000L; // Aug 13, 2020 2:59:59 AM, considered to be Aug 12.
    public static final long _2020_08_13__03_00_00_EDT = 1597302000000L; // Aug 13, 2020 3:00:00 AM
    public static final long _2020_08_13__23_59_59_EDT = 1597377599000L; // Aug 13, 2020 11:59:59 PM
    public static final long _2020_08_14__02_59_59_EDT = 1597388399000L; // Aug 14, 2020 2:59:59 AM, considered to be Aug 13.
    public static final long _2020_08_14__03_00_00_EDT = 1597388400000L; // Aug 14, 2020 3:00:00 AM

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
        List<String> checkedDates = ItineraryUtils.getDatesToCheckItineraryExistence(trip, false);
        Assertions.assertEquals(newDates, checkedDates);

        // If we forceAllDays to ItineraryUtils.getDatesToCheckItineraryExistence,
        // it should return all dates regardless of the ones set in the monitored trip.
        List<String> allDates = List.of(QUERY_DATE /* Thursday */, "2020-08-14", "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18", "2020-08-19");
        List<String> allCheckedDates = ItineraryUtils.getDatesToCheckItineraryExistence(trip, true);
        Assertions.assertEquals(allDates, allCheckedDates);
    }

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

    @Test
    public void testUpdateTripWithVerifiedItinerary() throws IOException, URISyntaxException {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "testUpdateTripWithVerifiedItinerary";
        trip.queryParams = BASE_QUERY + "&ui_activeItinerary=1";
        trip.itinerary = null;

        String mockResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse(mockResponse, URI.create("http://www.example.com"));
        List<Itinerary> itineraries = otpDispatcherResponse.getResponse().plan.itineraries;

        ItineraryUtils.updateTripWithVerifiedItinerary(trip, itineraries);

        Assertions.assertEquals(itineraries.get(1), trip.itinerary);
    }

    private void testIsSameDay(String time, boolean isArrival, boolean expected, Long... startTimes) throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip(isArrival);
        // The time zone for trip (and startTimes) is US ES Eastern per trip location.
        ZoneId zoneId = trip.timezoneForTargetLocation();

        for (Long startTime : startTimes) {
            Itinerary itinerary = simpleItinerary(startTime, isArrival);
            Assertions.assertEquals(
                expected,
                ItineraryUtils.isSameDay(itinerary, QUERY_DATE, time, zoneId, isArrival),
                String.format(
                    "%s %s be considered same day as %s %s",
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), zoneId),
                    expected ? "should" : "should not",
                    QUERY_DATE,
                    time
                )
            );
        }
    }

    @Test
    public void testItineraryDepartsSameDay() throws URISyntaxException {
        testIsSameDay(QUERY_TIME, false, true,
            _2020_08_13__03_00_00_EDT,
            _2020_08_13__23_59_59_EDT,
            _2020_08_14__02_59_59_EDT
        );
        testIsSameDay("1:23", false, true,
            _2020_08_12__03_00_00_EDT,
            _2020_08_12__23_59_59_EDT,
            _2020_08_13__02_59_59_EDT
        );
    }

    @Test
    public void testItineraryDoesNotDepartSameDay() throws URISyntaxException {
        testIsSameDay(QUERY_TIME, false, false,
            _2020_08_12__23_59_59_EDT,
            _2020_08_13__02_59_59_EDT,
            _2020_08_14__03_00_00_EDT
        );
        testIsSameDay("1:23", false, false,
            _2020_08_13__03_00_00_EDT,
            _2020_08_13__23_59_59_EDT,
            _2020_08_14__02_59_59_EDT
        );
    }

    @Test
    public void testItineraryArrivesSameDay() throws URISyntaxException {
        testIsSameDay(QUERY_TIME, true, true,
            _2020_08_13__03_00_00_EDT,
            _2020_08_13__23_59_59_EDT,
            _2020_08_14__02_59_59_EDT
        );
        testIsSameDay("1:23", true, true,
            _2020_08_12__03_00_00_EDT,
            _2020_08_12__23_59_59_EDT,
            _2020_08_13__02_59_59_EDT
        );
    }

    @Test
    public void testItineraryDoesNotArriveSameDay() throws URISyntaxException {
        testIsSameDay(QUERY_TIME, true, false,
            _2020_08_12__23_59_59_EDT,
            _2020_08_13__02_59_59_EDT,
            _2020_08_14__03_00_00_EDT
        );
        testIsSameDay("1:23", true, false,
            _2020_08_13__03_00_00_EDT,
            _2020_08_13__23_59_59_EDT,
            _2020_08_14__02_59_59_EDT
        );
    }

    @Test
    public void testGetSameDayItineraries() throws URISyntaxException {
        MonitoredTrip trip = makeTestTrip(false);
        trip.tripTime = QUERY_TIME;

        List<Long> startTimes = List.of(
            _2020_08_13__23_59_59_EDT, // same day
            _2020_08_14__02_59_59_EDT, // considered same day
            _2020_08_14__03_00_00_EDT // not same day
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

    private Itinerary simpleItinerary(Long startTime, boolean isArrival) {
        Itinerary itinerary = new Itinerary();
        Date date = Date.from(Instant.ofEpochMilli(startTime));
        if (isArrival) {
            itinerary.endTime = date;
        } else {
            itinerary.startTime = date;
        }
        itinerary.legs = new ArrayList<>();
        return itinerary;
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
        trip.queryParams = BASE_QUERY;
        trip.tripTime = QUERY_TIME;

        if (arriveBy) {
            trip.from = dummyPlace;
            trip.to = targetPlace;

            Map<String, String> baseQueryParams = trip.parseQueryParams();
            baseQueryParams.put("arriveBy", "true");
            trip.queryParams = ItineraryUtils.toQueryString(baseQueryParams);
        } else { // departBy
            trip.from = targetPlace;
            trip.to = dummyPlace;

            trip.queryParams = BASE_QUERY;
        }
        return trip;
    }
}
