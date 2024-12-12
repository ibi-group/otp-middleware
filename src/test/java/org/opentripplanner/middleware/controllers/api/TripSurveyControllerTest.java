package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.models.TripSurveyNotification;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;

class TripSurveyControllerTest extends OtpMiddlewareTestEnvironment {
    private static OtpUser otpUser;
    private static MonitoredTrip monitoredTrip;
    private static TrackedJourney trackedJourney;
    private static final String NOTIFICATION_ID = UUID.randomUUID().toString();

    @BeforeAll
    public static void setUp() throws Exception {
        assumeTrue(IS_END_TO_END);
        otpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-otpuser"));
        monitoredTrip = createMonitoredTrip();

        trackedJourney = new TrackedJourney();
        trackedJourney.id = UUID.randomUUID().toString();
        Persistence.trackedJourneys.create(trackedJourney);
    }

    private static MonitoredTrip createMonitoredTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = UUID.randomUUID().toString();
        trip.userId = otpUser.id;
        Persistence.monitoredTrips.create(trip);
        return trip;
    }

    @AfterAll
    public static void tearDown() throws Exception {
        assumeTrue(IS_END_TO_END);
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete(true);
        monitoredTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        if (monitoredTrip != null) monitoredTrip.delete();
        trackedJourney = Persistence.trackedJourneys.getById(trackedJourney.id);
        if (trackedJourney != null) trackedJourney.delete();
    }

    @BeforeEach
    void setUpTest() {
        otpUser.tripSurveyNotifications = List.of(
            new TripSurveyNotification("other-notification", Date.from(Instant.now()), "other-journey"),
            new TripSurveyNotification(NOTIFICATION_ID, Date.from(Instant.now()), trackedJourney.id)
        );
        Persistence.otpUsers.replace(otpUser.id, otpUser);
    }

    @Test
    void canMakeTripSurveyUrl() {
        assertEquals(
            "https://subdomain.typeform.com/to/survey-1#user_id=user-2&trip_id=trip-3&notification_id=notif-4",
            TripSurveyController.makeTripSurveyUrl("subdomain", "survey-1", "user-2", "trip-3", "notif-4")
        );
    }

    @Test
    void canOpenSurveyAndUpdateNotificationStatus() {
        assumeTrue(IS_END_TO_END);

        OtpUser existingUser = Persistence.otpUsers.getById(otpUser.id);
        assertNotNull(existingUser);
        existingUser.tripSurveyNotifications.forEach(n -> {
            assertNull(n.timeOpened);
        });

        Instant requestInstant = Instant.now();
        var response = makeRequest(
            String.format(
                "api/trip-survey/open?user_id=%s&trip_id=%s&notification_id=%s",
                otpUser.id,
                monitoredTrip.id,
                NOTIFICATION_ID
            ),
            "",
            Map.of(),
            HttpMethod.GET
        );
        Instant requestCompleteInstant = Instant.now();

        assertEquals(HttpStatus.OK_200, response.status);

        OtpUser updatedUser = Persistence.otpUsers.getById(otpUser.id);
        assertNotNull(updatedUser);
        assertEquals(otpUser.tripSurveyNotifications.size(), updatedUser.tripSurveyNotifications.size());

        int updatedNotificationCount = 0;
        for (TripSurveyNotification notification : updatedUser.tripSurveyNotifications) {
            if (NOTIFICATION_ID.equals(notification.id)) {
                assertNotNull(notification.timeOpened);
                assertTrue(notification.timeOpened.toInstant().isAfter(requestInstant));
                assertTrue(notification.timeOpened.toInstant().isBefore(requestCompleteInstant));
                updatedNotificationCount++;
            } else {
                assertNull(notification.timeOpened);
            }
        }
        assertEquals(1, updatedNotificationCount);
    }

    @ParameterizedTest
    @MethodSource("createShouldRejectInvalidParamsCases")
    void shouldRejectInvalidParams(String userId, String tripId, String notificationId) {
        assumeTrue(IS_END_TO_END);

        var response = makeRequest(
            String.format(
                "api/trip-survey/open?user_id=%s&trip_id=%s&notification_id=%s",
                userId,
                tripId,
                notificationId
            ),
            "",
            Map.of(),
            HttpMethod.GET
        );

        assertEquals(
            HttpStatus.BAD_REQUEST_400,
            response.status,
            "Invalid URL params should result in HTTP Status 400."
        );
    }

    private static Stream<Arguments> createShouldRejectInvalidParamsCases() {
        return Stream.of(
            Arguments.of("invalid-user-id", monitoredTrip.id, NOTIFICATION_ID),
            Arguments.of(null, monitoredTrip.id, NOTIFICATION_ID),
            Arguments.of(otpUser.id, "invalid-trip-id", NOTIFICATION_ID),
            Arguments.of(otpUser.id, null, NOTIFICATION_ID),
            Arguments.of(otpUser.id, monitoredTrip.id, "invalid-notification-id"),
            Arguments.of(otpUser.id, monitoredTrip.id, null)
        );
    }
}
