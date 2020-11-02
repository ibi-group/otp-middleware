package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.List;
import java.util.stream.Stream;

public class ItineraryUtilsTest {
    @ParameterizedTest
    @MethodSource("createItineraryCanBeMonitoredCases")
    public void testItineraryCanBeMonitored(Itinerary itinerary, boolean expectedResult, String message) {
        Assertions.assertEquals(expectedResult, ItineraryUtils.itineraryCanBeMonitored(itinerary), message);
    }

    private static Stream<Arguments> createItineraryCanBeMonitoredCases() {
        Leg transitLeg = new Leg();
        transitLeg.mode = "BUS";
        transitLeg.transitLeg = true;

        Leg rentalLeg = new Leg();
        rentalLeg.mode = "BICYCLE_RENT";
        rentalLeg.rentedVehicle = true;

        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";

        Leg rideHailLeg = new Leg();
        rideHailLeg.mode = "CAR_HAIL";
        rideHailLeg.hailedCar = true;

        Itinerary blankItinerary = new Itinerary();

        Itinerary itineraryWithTransitNoRentals = new Itinerary();
        itineraryWithTransitNoRentals.legs = List.of(transitLeg, walkLeg);

        Itinerary itineraryWithoutTransit = new Itinerary();
        itineraryWithoutTransit.legs = List.of(walkLeg, rentalLeg);

        Itinerary itineraryWithTransitAndRental = new Itinerary();
        itineraryWithTransitAndRental.legs = List.of(walkLeg, transitLeg, rentalLeg);

        Itinerary itineraryWithTransitAndRideHail = new Itinerary();
        itineraryWithTransitAndRideHail.legs = List.of(walkLeg, transitLeg, rideHailLeg);

        return Stream.of(
            Arguments.of(itineraryWithTransitNoRentals, true, "Itinerary with transit, no rentals/ride hail."),
            Arguments.of(itineraryWithoutTransit, false, "Itinerary without transit."),
            Arguments.of(itineraryWithTransitAndRental, false, "Itinerary with transit and rental."),
            Arguments.of(itineraryWithTransitAndRideHail, false, "Itinerary with transit and ride hail."),
            Arguments.of(blankItinerary, false, "Blank itinerary."),
            Arguments.of(null, false, "Null itinerary.")
        );
    }
}
