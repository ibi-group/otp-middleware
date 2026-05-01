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
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class contains tests for the {@link UnsnoozeTripsJob} class.
 */
class UnsnoozeTripsJobTest extends OtpMiddlewareTestEnvironment {
    private static OtpUser user;
    private static final List<String> createdTripIds = new ArrayList<>();

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
        createdTripIds.clear();
    }

    @Test
    void canGetTripsToUnsnooze() {
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        long nowMillis = now.toInstant().toEpochMilli();
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);
        long yesterdayMillis = someTimeYesterday.toInstant().toEpochMilli();

        Persistence.monitoredTrips.create(createSnoozedTrip(true, someTimeYesterday));
        Bson userFilter = eq("userId", user.id);
        MonitoredTrip snoozedTripFetched = Persistence.monitoredTrips.getOneFiltered(userFilter);

        Persistence.monitoredTrips.create(createActiveNotSnoozedTrip());
        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));

        // Get active snoozed trips.
        Bson filter = UnsnoozeTripsJob.makeActiveSnoozedTripsFilter();
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
    void canUnsnoozeTrips() {
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);

        Persistence.monitoredTrips.create(createActiveNotSnoozedTrip());
        Persistence.monitoredTrips.create(createSnoozedTrip(true, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));

        assertTrue(createdTripIds.containsAll(getTripIds(UnsnoozeTripsJob.getTripsToUnsnooze())));
        UnsnoozeTripsJob.unsnoozeTripsAsNeeded();
        assertTrue(UnsnoozeTripsJob.getTripsToUnsnooze().stream().noneMatch(t -> createdTripIds.contains(t.id)));
    }

    private static MonitoredTrip createActiveNotSnoozedTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = UUID.randomUUID().toString();
        trip.userId = user.id;
        createdTripIds.add(trip.id);
        return trip;
    }

    private static MonitoredTrip createSnoozedTrip(boolean isActive, ZonedDateTime lastChecked) {
        MonitoredTrip trip = createActiveNotSnoozedTrip();
        trip.isActive = isActive;
        trip.snoozed = true;
        trip.journeyState = new JourneyState();
        trip.journeyState.lastCheckedEpochMillis = lastChecked.toInstant().toEpochMilli();
        return trip;
    }

    private List<String> getTripIds(List<MonitoredTrip> trips) {
        return trips.stream().map(t -> t.id).collect(Collectors.toList());
    }
}
