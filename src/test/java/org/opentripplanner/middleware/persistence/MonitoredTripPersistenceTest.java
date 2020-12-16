package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.createMonitoredTrip;

/**
 * Tests to verify that monitored trip persistence in MongoDB collections are functioning properly. A
 * number of {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class MonitoredTripPersistenceTest {

    MonitoredTrip monitoredTrip = null;

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        OtpMiddlewareTest.setUp();
    }

    @Test
    public void canCreateMonitoredTrip() {
        String userId = "123456";
        monitoredTrip = createMonitoredTrip(userId);
        MonitoredTrip retrieved = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(monitoredTrip.id, retrieved.id, "Found monitored trip ID should equal inserted ID.");
    }

    @Test
    public void canDeleteMonitoredTrip() {
        String userId = "123456";
        MonitoredTrip monitoredTripToDelete = createMonitoredTrip(userId);
        Persistence.monitoredTrips.removeById(monitoredTripToDelete.id);
        MonitoredTrip monitoredTrip = Persistence.monitoredTrips.getById(monitoredTripToDelete.id);
        assertNull(monitoredTrip, "Deleted MonitoredTrip should no longer exist in database (should return as null).");
    }

    @AfterEach
    public void remove() {
        if (monitoredTrip != null) {
            Persistence.monitoredTrips.removeById(monitoredTrip.id);
        }
    }

}
