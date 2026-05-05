package org.opentripplanner.middleware.tripmonitor.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.firstItinerary;
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
    void canGetTripsToUnsnooze() throws JsonProcessingException {
        createTestTrips();
        List<UnsnoozeTripsJob.PartialTrip> snoozedTrips = UnsnoozeTripsJob.getSnoozedTrips();
        assertEquals(3, snoozedTrips.size());
        assertEquals(Set.of(createdTripIds.get(1), createdTripIds.get(3), createdTripIds.get(4)), getTripIds(snoozedTrips));

        List<UnsnoozeTripsJob.PartialTrip> tripsToUnsnooze = UnsnoozeTripsJob.getTripsToUnsnooze(snoozedTrips);
        assertEquals(2, tripsToUnsnooze.size());
        UnsnoozeTripsJob.PartialTrip partialTrip = tripsToUnsnooze.get(0);
        assertEquals(createdTripIds.get(1), partialTrip.id);
        assertEquals(createdTripIds.get(3), tripsToUnsnooze.get(1).id);

        MonitoredTrip fetchedTrip = Persistence.monitoredTrips.getById(partialTrip.id);
        assertTrue(fetchedTrip.snoozed);
        assertTrue(fetchedTrip.isActive);
    }

    @Test
    void canUnsnoozeTrips() throws JsonProcessingException {
        createTestTrips();
        Set<String> tripIdsToUnsnooze = UnsnoozeTripsJob.getTripIdsToUnsnooze();
        assertTrue(createdTripIds.containsAll(tripIdsToUnsnooze));
        new UnsnoozeTripsJob().run();
        assertTrue(UnsnoozeTripsJob.getTripIdsToUnsnooze().isEmpty());

        MonitoredTrip unsnoozedTrip = Persistence.monitoredTrips.getById(tripIdsToUnsnooze.stream().findFirst().get());
        assertFalse(unsnoozedTrip.snoozed);
        assertEquals(
            unsnoozedTrip.computeTargetZonedDateTime(unsnoozedTrip.itinerary).toLocalDate().toString(),
            unsnoozedTrip.journeyState.targetDate
        );
        assertEquals(
            unsnoozedTrip.journeyState.targetDate,
            DateTimeUtils.makeOtpZonedDateTime(unsnoozedTrip.journeyState.matchingItinerary.startTime).toLocalDate().toString()
        );
    }

    private static void createTestTrips() throws JsonProcessingException {
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        ZonedDateTime someTimeYesterday = now.minusDays(1).withHour(3);

        Persistence.monitoredTrips.create(createActiveNotSnoozedTrip());
        Persistence.monitoredTrips.create(createSnoozedTrip(true, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(false, someTimeYesterday));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now.minusDays(30)));
        Persistence.monitoredTrips.create(createSnoozedTrip(true, now));
    }

    private static MonitoredTrip createActiveNotSnoozedTrip() throws JsonProcessingException {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = UUID.randomUUID().toString();
        trip.userId = user.id;
        trip.itinerary = firstItinerary(OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE_TRIP_QUERIED_AT_MIDNIGHT.getResponse());
        createdTripIds.add(trip.id);
        return trip;
    }

    private static MonitoredTrip createSnoozedTrip(boolean isActive, ZonedDateTime lastChecked) throws JsonProcessingException {
        MonitoredTrip trip = createActiveNotSnoozedTrip();
        trip.isActive = isActive;
        trip.snoozed = true;
        trip.journeyState = new JourneyState();
        trip.journeyState.lastCheckedEpochMillis = lastChecked.toInstant().toEpochMilli();
        return trip;
    }
}
