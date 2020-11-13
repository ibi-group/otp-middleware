package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Holds tests for some methods in MonitoredTrip.
 */
public class MonitoredTripTest {
    /**
     * Abbreviated query params with mode params you would get from UI.
     */
    private static final String UI_QUERY_PARAMS
        = "?fromPlace=fromplace%3A%3A28.556631%2C-81.411781&toPlace=toplace%3A%3A28.545925%2C-81.348609&date=2020-11-13&time=14%3A21&arriveBy=false&mode=WALK%2CBUS%2CRAIL&numItineraries=3";

    /**
     * Partial test for {@link MonitoredTrip#initializeFromItineraryAndQueryParams}
     * that focuses on updating the mode in queryParams.
     */
    @Test
    public void canUpdateModeInQueryParams() throws URISyntaxException {
        // Setup a trip with an initial queryParams argument.
        MonitoredTrip trip = new MonitoredTrip();
        trip.queryParams = UI_QUERY_PARAMS;

        // Setup an itinerary returned by batch processing using other modes in the query params,
        // e.g. using bicycle+transit
        Leg bicycleLeg = new Leg();
        bicycleLeg.mode = "BICYCLE";

        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";

        Leg busLeg = new Leg();
        busLeg.mode = "BUS";

        Itinerary itinerary = new Itinerary();
        itinerary.legs = List.of(bicycleLeg, walkLeg, busLeg);
        trip.itinerary = itinerary;

        // Initialize internal trip vars.
        trip.initializeFromItineraryAndQueryParams();

        // Check that the mode was updated.
        Map<String, String> paramsMap = trip.parseQueryParams();
        String[] modeParams = paramsMap.get("mode").split(",");

        // If BICYCLE (or CAR or MICROMOBILITY...) and WALK appear together in an itinerary, usually the originating query doesn't mention WALK.
        List<String> expectedModeParams = List.of("BICYCLE", "BUS");
        List<String> actualModeParams = List.of(modeParams);

        Assertions.assertEquals(expectedModeParams.size(), actualModeParams.size());
        Assertions.assertTrue(expectedModeParams.containsAll(actualModeParams));
    }

    /**
     * Partial test for {@link MonitoredTrip#initializeFromItineraryAndQueryParams}
     * that focuses on removing the leading question mark in the query params.
     */
    @Test
    public void canRemoveLeadingQuestionMarkInQueryParams() throws URISyntaxException {
        // Setup a trip with an initial queryParams argument.
        MonitoredTrip trip = new MonitoredTrip();
        trip.queryParams = UI_QUERY_PARAMS;

        Map<String, String> paramsMap = trip.parseQueryParams();
        for (String key : paramsMap.keySet()) {
            Assertions.assertFalse(key.startsWith("?"));
        }
    }
}
