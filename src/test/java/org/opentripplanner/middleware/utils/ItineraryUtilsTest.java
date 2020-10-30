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
    @MethodSource("createItineraryHasTransitNoRentalsCases")
    public void testItineraryHasTransitNoRentals(Itinerary itinerary, boolean expectedResult) {
        Assertions.assertEquals(expectedResult, ItineraryUtils.itineraryHasTransitAndNoRentals(itinerary));
    }

    private static Stream<Arguments> createItineraryHasTransitNoRentalsCases() {
        Leg transitLeg = new Leg();
        transitLeg.mode = "BUS";

        Leg rentalLeg = new Leg();
        rentalLeg.mode = "BICYCLE_RENT";

        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";

        Itinerary blankItinerary = new Itinerary();

        Itinerary itineraryWithTransitNoRentals = new Itinerary();
        itineraryWithTransitNoRentals.legs = List.of(transitLeg, walkLeg);

        Itinerary itineraryWithoutTransit = new Itinerary();
        itineraryWithoutTransit.legs = List.of(walkLeg, rentalLeg);

        Itinerary itineraryWithTransitAndRental = new Itinerary();
        itineraryWithTransitAndRental.legs = List.of(walkLeg, transitLeg, rentalLeg);

        return Stream.of(
            Arguments.of(itineraryWithTransitNoRentals, true),
            Arguments.of(itineraryWithoutTransit, false),
            Arguments.of(itineraryWithTransitAndRental, false),
            Arguments.of(blankItinerary, false),
            Arguments.of(null, false)
        );
    }
}
