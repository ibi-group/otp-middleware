package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Place;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class OtpQueryUtilsTest {
    @Test
    public void testMakeQueriesWithNewDates() throws URISyntaxException {
        // Abbreviated query for this test.
        final String query = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";
        List<String> newDates = List.of("2020-12-30", "2020-12-31", "2021-01-01");

        List<String> result = OtpQueryUtils.makeQueryStringsWithNewDates(query, newDates);
        Assertions.assertEquals(newDates.size(), result.size());
        for (int i = 0; i < newDates.size(); i++) {
            // Insert a '?' in order to parse.
            Map<String, String> newParams = OtpQueryUtils.getQueryParams("?" + result.get(i));
            Assertions.assertEquals(newDates.get(i), newParams.get("date"));
        }
    }

    @Test
    public void testGetDatesForCheckingTripExistence() throws URISyntaxException {
        // Abbreviated query for this test.
        final String query = "?fromPlace=2418%20Dade%20Ave&toPlace=McDonald%27s&date=2020-08-13&time=11%3A23&arriveBy=false";

        Place fromPlace = new Place();
        fromPlace.lat = 33.80;
        fromPlace.lon = -84.70;

        MonitoredTrip trip = new MonitoredTrip();
        trip.id = "testGetDatesForCheckingTripExistence";
        trip.queryParams = query;
        trip.from = fromPlace;
        trip.monday = true;
        trip.tuesday = true;
        trip.wednesday = false;
        trip.thursday = true;
        trip.friday = false;
        trip.saturday = true;
        trip.sunday = true;

        // The days that are set to be monitored/checked will appear in the list
        // starting from the day of the query and up to 7 days.
        List<String> newDates = List.of("2020-08-13" /* Thursday */, "2020-08-15", "2020-08-16", "2020-08-17", "2020-08-18");
        List<String> checkedDates = OtpQueryUtils.getDatesForCheckingTripExistence(trip);

        Assertions.assertEquals(newDates, checkedDates);
    }
}
