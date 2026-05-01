package org.opentripplanner.middleware.tripmonitor.jobs;

import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class contains tests for the {@link UnsnoozeTripsJob} class.
 */
class UnsnoozeTripsJobTest extends OtpMiddlewareTestEnvironment {
    private static OtpUser user;

    @BeforeAll
    static void setup() {
        user = PersistenceTestUtils.createUser("user@example.com");
    }

    @AfterAll
    static void tearDown() {
        user.delete(false);
    }

    @AfterEach
    void cleanUpTest() {
        user.deleteOwnTrips();
    }

    @Test
    void canGetTripsToUnsnooze() throws Exception {
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        long nowMillis = now.toInstant().toEpochMilli();
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);
        long yesterdayMillis = someTimeYesterday.toInstant().toEpochMilli();

        MonitoredTrip snoozedTrip = createSnoozedTrip(true, someTimeYesterday);
        Persistence.monitoredTrips.create(snoozedTrip);
        Bson userFilter = eq("userId", user.id);
        MonitoredTrip snoozedTripFetched = Persistence.monitoredTrips.getOneFiltered(userFilter);

        // Default active trip.
        PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE,
            true,
            null
        );

        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));

        // Get active snoozed trips.
        Bson filter = and(
            UnsnoozeTripsJob.makeActiveSnoozedTripsFilter(),
            userFilter
        );
        var snoozedTrips = Persistence.monitoredTrips.getResponseList(filter, 0, 100);
        assertEquals(2, snoozedTrips.data.size());
        assertTrue(
            nowMillis == snoozedTrips.data.get(0).journeyState.lastCheckedEpochMillis ||
                nowMillis == snoozedTrips.data.get(1).journeyState.lastCheckedEpochMillis
        );
        assertTrue(
            yesterdayMillis == snoozedTrips.data.get(0).journeyState.lastCheckedEpochMillis ||
                yesterdayMillis == snoozedTrips.data.get(1).journeyState.lastCheckedEpochMillis
        );

        List<MonitoredTrip> tripsToUnsnooze = UnsnoozeTripsJob.getTripsToUnsnooze(snoozedTrips.data);
        assertEquals(1, tripsToUnsnooze.size());
        MonitoredTrip fetchedTrip = tripsToUnsnooze.get(0);
        assertEquals(snoozedTripFetched.id, fetchedTrip.id);
        assertTrue(fetchedTrip.snoozed);
        assertTrue(fetchedTrip.isActive);
    }

    @Test
    void canUnsnoozeTrips() throws Exception {
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);

        // Default active (nit snoozed) trip.
        PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE,
            true,
            null
        );
        Persistence.monitoredTrips.create(createSnoozedTrip(true, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));

        assertFalse(UnsnoozeTripsJob.getTripsToUnsnooze(user.id).isEmpty());
        UnsnoozeTripsJob.unsnoozeTripsAsNeeded();
        assertTrue(UnsnoozeTripsJob.getTripsToUnsnooze(user.id).isEmpty());
    }

    private static MonitoredTrip createSnoozedTrip(boolean isActive, ZonedDateTime lastChecked) {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = user.id;
        trip.isActive = isActive;
        trip.snoozed = true;
        trip.journeyState = new JourneyState();
        trip.journeyState.lastCheckedEpochMillis = lastChecked.toInstant().toEpochMilli();
        return trip;
    }
}
