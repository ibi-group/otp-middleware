package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.opentripplanner.middleware.utils.ItineraryUtils.MODE_PARAM;

/**
 * Holds tests for some methods in MonitoredTrip.
 */
class MonitoredTripTest {
    /**
     * Abbreviated query params with mode params you would get from UI.
     */
    private static final String UI_QUERY_PARAMS
        = "?fromPlace=fromplace%3A%3A28.556631%2C-81.411781&toPlace=toplace%3A%3A28.545925%2C-81.348609&date=2020-11-13&time=14%3A21&arriveBy=false&mode=WALK%2CBUS%2CRAIL&numItineraries=3";

    /**
     * Partial test for {@link MonitoredTrip#initializeFromItineraryAndQueryParams}
     * that focuses on updating the mode in queryParams.
     */
    @ParameterizedTest
    @MethodSource("createUpdateModeInQueryParamTestCases")
    void canUpdateModeInQueryParams(Leg accessLeg, Set<String> expectedModeParams) throws URISyntaxException {
        // Setup a trip with an initial queryParams argument.
        MonitoredTrip trip = new MonitoredTrip();
        trip.queryParams = UI_QUERY_PARAMS;

        // Setup an itinerary returned based on the provided accessLeg and a transit and walk leg.
        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";

        Leg busLeg = new Leg();
        busLeg.mode = "BUS";
        busLeg.transitLeg = true;

        Itinerary itinerary = new Itinerary();
        itinerary.legs = List.of(accessLeg, walkLeg, busLeg);
        trip.itinerary = itinerary;

        // Initialize internal trip vars.
        trip.initializeFromItineraryAndQueryParams();

        // Check that the mode was updated.
        Map<String, String> paramsMap = trip.parseQueryParams();
        String[] modeParams = paramsMap.get(MODE_PARAM).split(",");
        Set<String> actualModeParams = Set.of(modeParams);

        assertEquals(expectedModeParams, actualModeParams,
            String.format("This set of mode params %s is incorrect for access mode %s.", actualModeParams, accessLeg.mode)
        );
    }

    private static Stream<Arguments> createUpdateModeInQueryParamTestCases() {
        // User-owned bicycle leg for bicycle+transit itineraries.
        Leg bicycleLeg = new Leg();
        bicycleLeg.mode = "BICYCLE";

        // User-owned car leg for park-and-ride itineraries.
        // (hailedCar = false, rentedCar = false)
        Leg ownCarLeg = new Leg();
        ownCarLeg.mode = "CAR";

        // Rental car leg
        Leg rentalCarLeg = new Leg();
        rentalCarLeg.mode = "CAR";
        rentalCarLeg.rentedCar = true;

        // Hailed car leg
        Leg hailedCarLeg = new Leg();
        hailedCarLeg.mode = "CAR";
        hailedCarLeg.hailedCar = true;

        return Stream.of(
            // If BICYCLE (or MICROMOBILITY...) and WALK appear together in an itinerary,
            // removing WALK is necessary for OTP to return certain bicycle+transit itineraries.
            Arguments.of(bicycleLeg, Set.of("BICYCLE", "BUS")),

            // For itineraries with any CAR,
            // including WALK is necessary for OTP to return certain car+transit itineraries.
            Arguments.of(ownCarLeg, Set.of("CAR_PARK", "WALK", "BUS")),
            Arguments.of(rentalCarLeg, Set.of("CAR_RENT", "WALK", "BUS")),
            Arguments.of(hailedCarLeg, Set.of("CAR_HAIL", "WALK", "BUS"))
        );
    }

    /**
     * Partial test for {@link MonitoredTrip#initializeFromItineraryAndQueryParams}
     * that focuses on ignoring the leading question mark in the query params, if any.
     */
    @Test
    void canIgnoreLeadingQuestionMarkInQueryParams() throws URISyntaxException {
        // Setup a trip with an initial queryParams argument.
        MonitoredTrip trip = new MonitoredTrip();
        trip.queryParams = UI_QUERY_PARAMS;

        Map<String, String> paramsMap = trip.parseQueryParams();
        for (String key : paramsMap.keySet()) {
            assertFalse(key.startsWith("?"));
        }
    }

    @ParameterizedTest
    @MethodSource("createOneTimeTripCases")
    void canDetermineOneTimeTrips(MonitoredTrip trip, boolean isOneTime) {
        assertEquals(isOneTime, trip.isOneTime());
    }

    private static Stream<Arguments> createOneTimeTripCases() {
        return Stream.of(
            Arguments.of(createRecurrentTrip(), false),
            Arguments.of(new MonitoredTrip(), true)
        );
    }

    @ParameterizedTest
    @MethodSource("createInactiveTripCases")
    void canDetermineInactiveTrips(MonitoredTrip trip, boolean isInactive) {
        assertEquals(isInactive, trip.isInactive());
    }

    private static Stream<Arguments> createInactiveTripCases() {
        return Stream.of(
            Arguments.of(createRecurrentTrip(), false),
            Arguments.of(new MonitoredTrip(), false),
            Arguments.of(createInactiveTrip(), true)
        );
    }

    private static MonitoredTrip createRecurrentTrip() {
        MonitoredTrip recurrentTrip = new MonitoredTrip();
        recurrentTrip.tuesday = true;
        return recurrentTrip;
    }

    private static MonitoredTrip createInactiveTrip() {
        MonitoredTrip inactiveTrip = new MonitoredTrip();
        inactiveTrip.isActive = false;
        return inactiveTrip;
    }
}
