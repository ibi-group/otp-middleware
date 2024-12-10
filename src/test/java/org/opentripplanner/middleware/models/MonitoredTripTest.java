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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.models.RelatedUser.RelatedUserStatus.CONFIRMED;
import static org.opentripplanner.middleware.models.RelatedUser.RelatedUserStatus.PENDING;

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

    @ParameterizedTest
    @MethodSource("createGetAddedUsersCases")
    void canGetAddedUsers(MonitoredTrip.TripUsers originalUsers, MonitoredTrip.TripUsers finalUsers, MonitoredTrip.TripUsers expected) {
        MonitoredTrip originalTrip = new MonitoredTrip();
        originalTrip.primary = originalUsers.primary;
        originalTrip.companion = originalUsers.companion;
        originalTrip.observers = originalUsers.observers;

        MonitoredTrip finalTrip = new MonitoredTrip();
        finalTrip.primary = finalUsers.primary;
        finalTrip.companion = finalUsers.companion;
        finalTrip.observers = finalUsers.observers;

        MonitoredTrip.TripUsers tripUsers = MonitoredTrip.getAddedUsers(finalTrip, originalTrip);

        assertEquals(expected.primary != null, tripUsers.primary != null);
        if (expected.primary != null && tripUsers.primary != null) {
            assertEquals(expected.primary.userId, tripUsers.primary.userId);
        }
        assertEquals(expected.companion != null, tripUsers.companion != null);
        if (expected.companion != null && tripUsers.companion != null) {
            assertEquals(expected.companion.email, tripUsers.companion.email);
        }
        assertEquals(expected.observers, tripUsers.observers);
    }

    private static Stream<Arguments> createGetAddedUsersCases() {
        MobilityProfileLite primary = new MobilityProfileLite();
        primary.userId = "primary-user-id";

        MobilityProfileLite newPrimary = new MobilityProfileLite();
        newPrimary.userId = "new-primary-user-id";

        RelatedUser companion = new RelatedUser("companion@example.com", CONFIRMED);
        RelatedUser newCompanion = new RelatedUser("new-companion@example.com", CONFIRMED);
        RelatedUser unconfirmedCompanion = new RelatedUser("unconfirmed-companion@example.com", PENDING);

        RelatedUser observer1 = new RelatedUser("observer1@example.com", CONFIRMED);
        RelatedUser observer2 = new RelatedUser("observer2@example.com", CONFIRMED);
        RelatedUser observer3 = new RelatedUser("observer3@example.com", CONFIRMED);

        List<RelatedUser> observers = List.of(observer1, observer2);

        return Stream.of(
            // If the final trip has the same users as the original one, no one has been added.
            Arguments.of(
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(null, null, new ArrayList<>())
            ),
            // If the final trip drops original users without adding new ones, no one has been added.
            Arguments.of(
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(null, null, List.of()),
                new MonitoredTrip.TripUsers(null, null, new ArrayList<>())
            ),
            // If the original trip did not include users, users in the final trip have been added.
            Arguments.of(
                new MonitoredTrip.TripUsers(null, null, List.of()),
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(primary, companion, observers)
            ),
            // If users have been modified, the modified users should appear in added users.
            Arguments.of(
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(newPrimary, newCompanion, List.of(observer1, observer3)),
                new MonitoredTrip.TripUsers(newPrimary, newCompanion, List.of(observer3))
            ),
            // If users have been modified, unconfirmed users should not be added.
            Arguments.of(
                new MonitoredTrip.TripUsers(primary, companion, observers),
                new MonitoredTrip.TripUsers(newPrimary, unconfirmedCompanion, List.of(observer1, unconfirmedCompanion)),
                new MonitoredTrip.TripUsers(newPrimary, null, List.of())
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createGetPrimaryTravelerCases")
    void canGetPrimaryTraveler(MobilityProfileLite primary, String creatorId, String expectedId, String message) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = creatorId;
        trip.primary = primary;

        assertEquals(expectedId, trip.getPrimaryTravelerId(), message);
    }

    private static Stream<Arguments> createGetPrimaryTravelerCases() {
        final String creatorId = "creator-user-id";

        MobilityProfileLite primary = new MobilityProfileLite();
        primary.userId = "primary-user-id";

        return Stream.of(
            Arguments.of(
                primary,
                creatorId,
                primary.userId,
                "Should return the id of primary if it was set."
            ),
            Arguments.of(
                null,
                creatorId,
                creatorId,
                "Should return the id of the user that created the trip if primary is null."
            )
        );
    }
}
