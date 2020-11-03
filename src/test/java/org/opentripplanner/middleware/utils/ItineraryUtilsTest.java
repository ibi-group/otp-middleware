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

        Leg rentalBikeLeg = new Leg();
        rentalBikeLeg.mode = "BICYCLE_RENT";
        rentalBikeLeg.rentedBike = true;

        Leg rentalCarLeg = new Leg();
        rentalCarLeg.mode = "CAR_RENT";
        rentalCarLeg.rentedCar = true;

        Leg rentalMicromobilityLeg = new Leg();
        rentalMicromobilityLeg.mode = "MICROMOBILITY_RENT";
        rentalMicromobilityLeg.rentedVehicle = true;

        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";

        Leg rideHailLeg = new Leg();
        rideHailLeg.mode = "CAR_HAIL";
        rideHailLeg.hailedCar = true;

        Itinerary blankItinerary = new Itinerary();

        Itinerary itineraryWithTransitNoRentals = new Itinerary();
        itineraryWithTransitNoRentals.legs = List.of(transitLeg, walkLeg);

        Itinerary itineraryWithRentalBikeWithoutTransit = new Itinerary();
        itineraryWithRentalBikeWithoutTransit.legs = List.of(walkLeg, rentalBikeLeg);

        Itinerary itineraryWithTransitAndRentalBike = new Itinerary();
        itineraryWithTransitAndRentalBike.legs = List.of(walkLeg, transitLeg, rentalBikeLeg);

        Itinerary itineraryWithTransitAndRentalCar = new Itinerary();
        itineraryWithTransitAndRentalCar.legs = List.of(walkLeg, transitLeg, rentalCarLeg);

        Itinerary itineraryWithTransitAndRentalMicromobility = new Itinerary();
        itineraryWithTransitAndRentalMicromobility.legs = List.of(walkLeg, transitLeg, rentalMicromobilityLeg);

        Itinerary itineraryWithTransitAndRideHail = new Itinerary();
        itineraryWithTransitAndRideHail.legs = List.of(walkLeg, transitLeg, rideHailLeg);

        return Stream.of(
            Arguments.of(itineraryWithTransitNoRentals, true, "Itinerary with transit, no rentals/ride hail."),
            Arguments.of(itineraryWithRentalBikeWithoutTransit, false, "Itinerary without transit."),
            Arguments.of(itineraryWithTransitAndRentalBike, false, "Itinerary with transit and rental bike."),
            Arguments.of(itineraryWithTransitAndRentalCar, false, "Itinerary with transit and rental car."),
            Arguments.of(itineraryWithTransitAndRideHail, false, "Itinerary with transit and ride hail."),
            Arguments.of(blankItinerary, false, "Blank itinerary."),
            Arguments.of(null, false, "Null itinerary.")
        );
    }
}
