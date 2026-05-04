package org.opentripplanner.middleware.tripmonitor.jobs;

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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.tripmonitor.jobs.UnsnoozeTripsJob.getTripIds;

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
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);

        Persistence.monitoredTrips.create(createSnoozedTrip(true, someTimeYesterday));
        Persistence.monitoredTrips.create(createActiveNotSnoozedTrip());
        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));

        List<UnsnoozeTripsJob.PartialTrip> snoozedTrips = UnsnoozeTripsJob.getSnoozedTrips();
        assertEquals(2, snoozedTrips.size());
        assertEquals(Set.of(createdTripIds.get(0), createdTripIds.get(3)), getTripIds(snoozedTrips));

        List<UnsnoozeTripsJob.PartialTrip> tripsToUnsnooze = UnsnoozeTripsJob.getTripsToUnsnooze(snoozedTrips);
        assertEquals(1, tripsToUnsnooze.size());
        UnsnoozeTripsJob.PartialTrip fetchedTrip = tripsToUnsnooze.get(0);
        assertEquals(createdTripIds.get(0), fetchedTrip.id);
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

        assertTrue(createdTripIds.containsAll(UnsnoozeTripsJob.getTripIdsToUnsnooze()));
        UnsnoozeTripsJob.unsnoozeTripsAsNeeded();
        assertTrue(UnsnoozeTripsJob.getTripIdsToUnsnooze().stream().noneMatch(createdTripIds::contains));
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
}
