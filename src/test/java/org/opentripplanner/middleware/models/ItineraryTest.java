package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.List;
import java.util.stream.Stream;

public class ItineraryTest {
    private static final Itinerary blankItinerary = new Itinerary();
    private static Itinerary itineraryWithTransitNoRentals;
    private static Itinerary itineraryWithRentalBikeWithoutTransit;
    private static Itinerary itineraryWithTransitAndRentalBike;
    private static Itinerary itineraryWithTransitAndRentalCar;
    private static Itinerary itineraryWithTransitAndRentalMicromobility;
    private static Itinerary itineraryWithTransitAndRideHail;

    @BeforeAll
    public static void setUp() {
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

        itineraryWithTransitNoRentals = new Itinerary();
        itineraryWithTransitNoRentals.legs = List.of(transitLeg, walkLeg);

        itineraryWithRentalBikeWithoutTransit = new Itinerary();
        itineraryWithRentalBikeWithoutTransit.legs = List.of(walkLeg, rentalBikeLeg);

        itineraryWithTransitAndRentalBike = new Itinerary();
        itineraryWithTransitAndRentalBike.legs = List.of(walkLeg, transitLeg, rentalBikeLeg);

        itineraryWithTransitAndRentalCar = new Itinerary();
        itineraryWithTransitAndRentalCar.legs = List.of(walkLeg, transitLeg, rentalCarLeg);

        itineraryWithTransitAndRentalMicromobility = new Itinerary();
        itineraryWithTransitAndRentalMicromobility.legs = List.of(walkLeg, transitLeg, rentalMicromobilityLeg);

        itineraryWithTransitAndRideHail = new Itinerary();
        itineraryWithTransitAndRideHail.legs = List.of(walkLeg, transitLeg, rideHailLeg);
    }

    @ParameterizedTest
    @MethodSource("createItineraryHasTransitCases")
    public void canCheckItineraryHasTransit(Itinerary itinerary, boolean expectedResult, String message) {
        Assertions.assertEquals(expectedResult, itinerary.hasTransit(), message);
    }

    private static Stream<Arguments> createItineraryHasTransitCases() {
        return Stream.of(
            Arguments.of(itineraryWithTransitNoRentals, true, "Itinerary with transit."),
            Arguments.of(itineraryWithRentalBikeWithoutTransit, false, "Itinerary without transit."),
            Arguments.of(blankItinerary, false, "Blank itinerary.")
        );
    }

    @ParameterizedTest
    @MethodSource("createItineraryHasRentalOrRideHailCases")
    public void canCheckItineraryHasRentalOrRideHail(Itinerary itinerary, boolean expectedResult, String message) {
        Assertions.assertEquals(expectedResult, itinerary.hasRentalOrRideHail(), message);
    }

    private static Stream<Arguments> createItineraryHasRentalOrRideHailCases() {
        return Stream.of(
            Arguments.of(itineraryWithTransitNoRentals, false, "Itinerary with transit, no rentals/ride hail."),
            Arguments.of(itineraryWithTransitAndRentalBike, true, "Itinerary with transit and rental bike."),
            Arguments.of(itineraryWithTransitAndRentalCar, true, "Itinerary with transit and rental car."),
            Arguments.of(itineraryWithTransitAndRentalMicromobility, true, "Itinerary with transit and rental micromobility."),
            Arguments.of(itineraryWithTransitAndRideHail, true, "Itinerary with transit and ride hail."),
            Arguments.of(blankItinerary, false, "Blank itinerary.")
        );
    }
}
