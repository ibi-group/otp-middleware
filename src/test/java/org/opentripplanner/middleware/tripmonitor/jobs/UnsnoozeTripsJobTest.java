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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.firstItinerary;

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
        List<MonitoredTrip> snoozedTrips = UnsnoozeTripsJob.getSnoozedTrips();
        assertEquals(3, snoozedTrips.size());
        assertEquals(Set.of(createdTripIds.get(1), createdTripIds.get(3), createdTripIds.get(4)), getTripIds(snoozedTrips));

        List<MonitoredTrip> tripsToUnsnooze = UnsnoozeTripsJob.getTripsToUnsnooze(snoozedTrips);
        assertEquals(2, tripsToUnsnooze.size());
        MonitoredTrip tripToUnsnooze = tripsToUnsnooze.get(0);
        assertEquals(createdTripIds.get(1), tripToUnsnooze.id);
        assertTrue(tripToUnsnooze.snoozed);
        assertTrue(tripToUnsnooze.isActive);
        assertEquals(createdTripIds.get(3), tripsToUnsnooze.get(1).id);
    }

    @Test
    void canUnsnoozeTrips() throws JsonProcessingException {
        createTestTrips();
        Set<String> tripIdsToUnsnooze = getTripIdsToUnsnooze();
        assertTrue(createdTripIds.containsAll(tripIdsToUnsnooze));
        new UnsnoozeTripsJob().run();
        assertTrue(getTripIdsToUnsnooze().isEmpty());

        Optional<String> tripIdToUnsnooze = tripIdsToUnsnooze.stream().findFirst();
        assertTrue(tripIdToUnsnooze.isPresent());
        MonitoredTrip unsnoozedTrip = Persistence.monitoredTrips.getById(tripIdToUnsnooze.get());
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

    private static Set<String> getTripIdsToUnsnooze() {
        return getTripIds(UnsnoozeTripsJob.getTripsToUnsnooze(UnsnoozeTripsJob.getSnoozedTrips()));
    }

    private static Set<String> getTripIds(List<MonitoredTrip> trips) {
        return trips.stream().map(t -> t.id).collect(Collectors.toSet());
    }
}
