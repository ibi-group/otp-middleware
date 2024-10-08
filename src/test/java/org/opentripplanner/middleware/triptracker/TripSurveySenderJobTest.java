package org.opentripplanner.middleware.triptracker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.models.OtpUser.LAST_TRIP_SURVEY_NOTIF_SENT_FIELD;
import static org.opentripplanner.middleware.models.TrackedJourney.FORCIBLY_TERMINATED;
import static org.opentripplanner.middleware.models.TrackedJourney.TERMINATED_BY_USER;

class TripSurveySenderJobTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser user1notifiedNow;
    private static OtpUser user2notifiedAWeekAgo;
    private static OtpUser user3neverNotified;

    private static List<OtpUser> otpUsers = List.of();
    private static List<TrackedJourney> journeys = List.of();
    private static MonitoredTrip trip;
    private static final Date EIGHT_DAYS_AGO = Date.from(Instant.now().minus(8, ChronoUnit.DAYS));

    @BeforeAll
    public static void setUp() {
        assumeTrue(IS_END_TO_END);

        // Create users. and populate the date for last trip survey notification.
        user1notifiedNow = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-user1"));
        user2notifiedAWeekAgo = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-user2"));
        user3neverNotified = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-user3"));

        user1notifiedNow.lastTripSurveyNotificationSent = new Date();
        user2notifiedAWeekAgo.lastTripSurveyNotificationSent = EIGHT_DAYS_AGO;
        user3neverNotified.lastTripSurveyNotificationSent = null;

        otpUsers = List.of(user1notifiedNow, user2notifiedAWeekAgo, user3neverNotified);
        otpUsers.forEach(user -> Persistence.otpUsers.replace(user.id, user));

        // Use one user for all trips and journeys (trips will be deleted with OtpUser.delete() on tear down.
        trip = new MonitoredTrip();
        trip.id = String.format("%s-trip-id", user1notifiedNow.id);
        trip.userId = user2notifiedAWeekAgo.id;
        Persistence.monitoredTrips.create(trip);
    }

    @AfterAll
    public static void tearDown() {
        assumeTrue(IS_END_TO_END);
        
        // Delete users
        otpUsers.forEach(user -> {
            OtpUser storedUser = Persistence.otpUsers.getById(user.id);
            if (storedUser != null) storedUser.delete(false);
        });
    }
    
    @AfterEach
    void afterEach() {
        assumeTrue(IS_END_TO_END);

        // Delete journeys
        for (TrackedJourney journey : journeys) {
            TrackedJourney storedJourney = Persistence.trackedJourneys.getById(journey.id);
            if (storedJourney != null) storedJourney.delete();
        }

        // Reset notification sent time to affected user.
        Persistence.otpUsers.updateField(user2notifiedAWeekAgo.id, LAST_TRIP_SURVEY_NOTIF_SENT_FIELD, EIGHT_DAYS_AGO);
    }

    @Test
    void canGetUsersWithNotificationsOverAWeekAgo() {
        assumeTrue(IS_END_TO_END);

        List<OtpUser> usersWithNotificationsOverAWeekAgo = TripSurveySenderJob.getUsersWithNotificationsOverAWeekAgo();
        assertEquals(1, usersWithNotificationsOverAWeekAgo.size());
        assertEquals(user2notifiedAWeekAgo.id, usersWithNotificationsOverAWeekAgo.get(0).id);
    }

    @Test
    void canGetCompletedJourneysInPast24To48Hours() {
        assumeTrue(IS_END_TO_END);
        createTestJourneys();

        List<TrackedJourney> completedJourneys = TripSurveySenderJob.getCompletedJourneysInPast24To48Hours();
        assertEquals(2, completedJourneys.size());
    }

    private static TrackedJourney createJourney(
        String id,
        String tripId,
        Instant endTime,
        String endCondition,
        Double deviation
    ) {
        TrackedJourney journey = new TrackedJourney();
        journey.id = id;
        journey.tripId = tripId;
        journey.endCondition = endCondition;
        journey.endTime = endTime != null ? Date.from(endTime) : null;
        journey.totalDeviation = deviation;
        Persistence.trackedJourneys.create(journey);
        return journey;
    }

    @Test
    void canMapJourneysToUsers() {
        assumeTrue(IS_END_TO_END);
        createTestJourneys();

        List<MonitoredTrip> trips = TripSurveySenderJob.getTripsForJourneysAndUsers(journeys, otpUsers);
        assertEquals(1, trips.size());
        assertEquals(trip.id, trips.get(0).id);

        Map<OtpUser, List<TrackedJourney>> usersToJourneys = TripSurveySenderJob.mapJourneysToUsers(journeys, otpUsers);
        assertEquals(1, usersToJourneys.size());
        List<TrackedJourney> userJourneys = usersToJourneys.get(otpUsers.get(1));
        assertEquals(5, userJourneys.size());
        assertTrue(userJourneys.containsAll(journeys.subList(0, 4)));
    }

    @Test
    void canSelectMostDeviatedJourney() {
        TrackedJourney journey1 = new TrackedJourney();
        journey1.totalDeviation = 250.0;
        journey1.endTime = Date.from(Instant.now().minus(3, ChronoUnit.HOURS));

        TrackedJourney journey2 = new TrackedJourney();
        journey2.totalDeviation = 400.0;
        journey2.endTime = Date.from(Instant.now().minus(5, ChronoUnit.HOURS));

        Optional<TrackedJourney> optJourney = TripSurveySenderJob.selectMostDeviatedJourney(List.of(journey1, journey2));
        assertTrue(optJourney.isPresent());
        assertEquals(journey2, optJourney.get());
    }

    @Test
    void canRunJob() {
        assumeTrue(IS_END_TO_END);
        createTestJourneys();

        Date start = new Date();
        OtpUser storedUser = Persistence.otpUsers.getById(user2notifiedAWeekAgo.id);
        assertFalse(start.before(storedUser.lastTripSurveyNotificationSent));

        TripSurveySenderJob job = new TripSurveySenderJob();
        job.run();

        storedUser = Persistence.otpUsers.getById(user2notifiedAWeekAgo.id);
        assertTrue(start.before(storedUser.lastTripSurveyNotificationSent));

        // Other user last notification should not have changed.
        storedUser = Persistence.otpUsers.getById(user1notifiedNow.id);
        assertFalse(start.before(storedUser.lastTripSurveyNotificationSent));
        storedUser = Persistence.otpUsers.getById(user3neverNotified.id);
        assertNull(storedUser.lastTripSurveyNotificationSent);
    }

    private static void createTestJourneys() {
        Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant thirtyHoursAgo = Instant.now().minus(30, ChronoUnit.HOURS);
        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);

        // Create journey for each trip for all users above (they will be deleted explicitly after each test).
        journeys = List.of(
            // Ongoing journey (should not be included)
            createJourney("ongoing-journey", trip.id, null, null, 10.0),

            // Journey completed by user 30 hours ago (should be included)
            createJourney("user-terminated-journey", trip.id, thirtyHoursAgo, TERMINATED_BY_USER, 200.0),

            // Journey terminated forcibly 30 hours ago (should be included)
            createJourney("forcibly-terminated-journey", trip.id, thirtyHoursAgo, FORCIBLY_TERMINATED, 400.0),

            // Additional journey completed over 48 hours (should not be included).
            createJourney("journey-done-3-days-ago", trip.id, threeDaysAgo, TERMINATED_BY_USER, 10.0),

            // Additional journey completed within 24 hours (should not be included).
            createJourney("journey-done-3-hours-ago", trip.id, threeHoursAgo, TERMINATED_BY_USER, 10.0),

            // Orphan journeys (should not be included)
            createJourney("journey-3", "other-trip", null, null, 10.0),
            createJourney("journey-4", "other-trip", null, null, 0.0)
        );
    }
}

