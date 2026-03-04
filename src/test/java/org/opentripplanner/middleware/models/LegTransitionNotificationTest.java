package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.createDefaultItinerary;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.firstItinerary;

class LegTransitionNotificationTest extends OtpMiddlewareTestEnvironment {
    private static OtpUser primary;
    private static OtpUser companion;
    private static OtpUser observer;

    @BeforeAll
    public static void setup() throws Exception {
        primary = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-primary-user"));
        companion = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-companion-user"));
        observer = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-observer-user"));
    }

    @AfterAll
    public static void tearDown() {
        PersistenceTestUtils.deleteOtpUser(false, primary, companion, observer);
    }

    @ParameterizedTest
    @MethodSource("createLegTransitionNotificationTestCases")
    void testLegTransitionNotifications(
        NotificationType notificationType,
        String travelerName,
        TravelerPosition travelerPosition,
        String message
    ) {
        TripMonitorNotification notification = new LegTransitionNotification(
            travelerName,
            notificationType,
            travelerPosition,
            Locale.US
        ).tripMonitorNotification;
        assertNotNull(notification);
        assertEquals(message, notification.body);
    }

    private static Stream<Arguments> createLegTransitionNotificationTestCases() throws Exception {
        String travelerName = "Obi-Wan";
        Itinerary itinerary = firstItinerary(OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getResponse());
        Leg expectedLeg = itinerary.legs.get(1);
        Coordinates expectedLegDestinationCoords = new Coordinates(expectedLeg.to);
        Leg nextLeg = itinerary.legs.get(2);
        Coordinates nextLegDepartureCoords = new Coordinates(nextLeg.from);
        return Stream.of(
            Arguments.of(
                NotificationType.MODE_CHANGE_NOTIFICATION,
                travelerName,
                new TravelerPosition.Builder()
                    .setExpectedLeg(expectedLeg)
                    .setNextLeg(nextLeg)
                    .setCurrentPosition(expectedLegDestinationCoords)
                    .build(),
                "Obi-Wan has arrived at transit stop Pioneer Square South MAX Station."
            ),
            Arguments.of(
                NotificationType.DEPARTED_NOTIFICATION,
                travelerName,
                new TravelerPosition.Builder()
                    .setExpectedLeg(expectedLeg)
                    .setNextLeg(nextLeg)
                    .setCurrentPosition(nextLegDepartureCoords)
                    .build(),
                "Obi-Wan has departed Providence Park MAX Station."
            ),
            Arguments.of(
                NotificationType.ARRIVED_NOTIFICATION,
                travelerName,
                new TravelerPosition.Builder()
                    .setExpectedLeg(expectedLeg)
                    .setNextLeg(nextLeg)
                    .setCurrentPosition(expectedLegDestinationCoords)
                    .build(),
                "Obi-Wan has arrived at Pioneer Square South MAX Station."
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createLegTransitionNotifyUsersTestCases")
    void testLegTransitionNotifyUsers(
        String tripOwnerUserId,
        Set<OtpUser> expectedUsers
    ) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = tripOwnerUserId;
        // Most commonly, if the primary traveler is the creator of the trip and there are no companions,
        // the `primary` field will be null, and `userId` will be considered the primary traveler.
        if (!tripOwnerUserId.equals((primary.id))) {
            trip.primary = new MobilityProfileLite(primary);
        }
        trip.companion = new RelatedUser(companion.email, RelatedUser.RelatedUserStatus.CONFIRMED);
        trip.observers.add(new RelatedUser(observer.email, RelatedUser.RelatedUserStatus.CONFIRMED));

        Set<OtpUser> users = LegTransitionNotification.getLegTransitionNotifyUsers(trip);
        assertNotNull(users);
        assertTrue(users.containsAll(expectedUsers));
        assertEquals(users.size(), expectedUsers.size());
    }

    private static Stream<Arguments> createLegTransitionNotifyUsersTestCases() {
        return Stream.of(
            Arguments.of(primary.id, Set.of(companion, observer)),
            Arguments.of(companion.id, Set.of(primary, observer))
        );
    }

    @Test
    void testLegTransitionNotifyUsersIncompleteData() {
        MonitoredTrip trip = new MonitoredTrip();
        // Set as owner an existing user that is not the primary or the companion user from the setup method.
        trip.userId = observer.id;
        trip.primary = new MobilityProfileLite();
        trip.companion = new RelatedUser();
        trip.observers.add(new RelatedUser());

        Set<OtpUser> users = LegTransitionNotification.getLegTransitionNotifyUsers(trip);
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }
}