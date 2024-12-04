package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest
    @MethodSource("createHasCompanionCases")
    void testHasCompanion(RelatedUser companion, boolean expected) {
        MonitoredTrip ownTripWithCompanion = new MonitoredTrip();
        ownTripWithCompanion.companion = companion;
        ownTripWithCompanion.userId = "trip-user-id";

        assertEquals(expected, ownTripWithCompanion.hasConfirmedCompanion());
    }

    private static Stream<Arguments> createHasCompanionCases() {
        RelatedUser confirmedCompanion = new RelatedUser();
        confirmedCompanion.email = "companion@example.com";
        confirmedCompanion.status = RelatedUser.RelatedUserStatus.CONFIRMED;

        RelatedUser unconfirmedCompanion = new RelatedUser();
        unconfirmedCompanion.email = "companion@example.com";
        unconfirmedCompanion.status = RelatedUser.RelatedUserStatus.INVALID;

        return Stream.of(
            Arguments.of(null, false),
            Arguments.of(confirmedCompanion, true),
            Arguments.of(unconfirmedCompanion, false)
        );
    }
}
