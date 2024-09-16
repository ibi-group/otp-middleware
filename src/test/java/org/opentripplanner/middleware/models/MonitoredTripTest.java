package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.OtpGraphQLTransportMode;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitoredTripTest {
    @Test
    void initializeFromItineraryAndQueryParamsShouldNotModifyModes() {
        // The list of modes is provided by the UI/mobile app client,
        // and can (mistakenly?) contain duplicate modes.
        var originalModes = Stream
            .of("BUS", "TRAM", "RAIL", "FERRY", "BUS", "TRAM")
            .map(OtpGraphQLTransportMode::fromModeString)
            .collect(Collectors.toList());

        OtpGraphQLVariables variables = new OtpGraphQLVariables();
        variables.time = "14:53";
        variables.modes = List.copyOf(originalModes);

        Itinerary itinerary = new Itinerary();
        Leg leg = new Leg();
        leg.mode = "BUS";
        leg.from = new Place();
        leg.to = new Place();
        itinerary.legs = List.of(leg);

        MonitoredTrip trip = new MonitoredTrip();
        trip.otp2QueryParams = variables;
        trip.itinerary = itinerary;

        trip.initializeFromItineraryAndQueryParams(variables);
        assertEquals(originalModes, trip.otp2QueryParams.modes);
    }
}
