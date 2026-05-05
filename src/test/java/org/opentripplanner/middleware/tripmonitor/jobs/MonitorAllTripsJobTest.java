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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NO_LONGER_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.PAST_TRIP;

/**
 * This class contains tests for the {@link MonitorAllTripsJob} class.
 */
class MonitorAllTripsJobTest extends OtpMiddlewareTestEnvironment {
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
    void canFilterOutTrips() throws Exception {
        // Default active trip.
        PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE,
            true,
            null
        );
        Bson userFilter = eq("userId", user.id);
        MonitoredTrip activeTrip = Persistence.monitoredTrips.getOneFiltered(userFilter);

        MonitoredTrip inactiveTrip = createInactiveTrip();
        Persistence.monitoredTrips.create(inactiveTrip);

        MonitoredTrip obsoleteTrip = createObsoleteTrip();
        Persistence.monitoredTrips.create(obsoleteTrip);

        MonitoredTrip oneTimePastTrip = createOneTimePastTrip();
        Persistence.monitoredTrips.create(oneTimePastTrip);

        MonitoredTrip snoozedTrip = createSnoozedTrip();
        Persistence.monitoredTrips.create(snoozedTrip);

        Bson filter = and(
            MonitorAllTripsJob.makeTripFilter(),
            userFilter
        );

        var trips = Persistence.monitoredTrips.getResponseList(filter, 0, 100);
        assertEquals(1, trips.data.size());
        assertEquals(activeTrip.id, trips.data.get(0).id);
    }

    private static MonitoredTrip createOneTimePastTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = user.id;
        trip.isActive = true;
        trip.journeyState = new JourneyState();
        trip.journeyState.tripStatus = PAST_TRIP;
        trip.updateAllDaysOfWeek(false);
        return trip;
    }

    private static MonitoredTrip createObsoleteTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = user.id;
        trip.isActive = true;
        trip.journeyState = new JourneyState();
        trip.journeyState.tripStatus = NO_LONGER_POSSIBLE;
        return trip;
    }

    private static MonitoredTrip createInactiveTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.userId = user.id;
        trip.isActive = false;
        return trip;
    }

    private static MonitoredTrip createActiveNotSnoozedTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = UUID.randomUUID().toString();
        trip.userId = user.id;
        createdTripIds.add(trip.id);
        return trip;
    }

    private static MonitoredTrip createSnoozedTrip() {
        MonitoredTrip trip = createActiveNotSnoozedTrip();
        trip.snoozed = true;
        return trip;
    }
}
