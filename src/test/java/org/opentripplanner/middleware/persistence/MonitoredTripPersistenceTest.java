package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;

/**
 * Tests to verify that monitored trip persistence in MongoDB collections are functioning properly. A
 * number of {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class MonitoredTripPersistenceTest  extends OtpMiddlewareTest {

    MonitoredTrip monitoredTrip = null;

    @Test
    public void canCreateMonitoredTrip() {
        String userId = "123456";
        monitoredTrip = createMonitoredTrip(userId);
        MonitoredTrip retrieved = Persistence.monitoredTrip.getById(monitoredTrip.id);
        assertEquals(monitoredTrip.id, retrieved.id, "Found monitored trip ID should equal inserted ID.");
    }

    @Test
    public void canDeleteMonitoredTrip() {
        String userId = "123456";
        MonitoredTrip monitoredTripToDelete = createMonitoredTrip(userId);
        Persistence.monitoredTrip.removeById(monitoredTripToDelete.id);
        MonitoredTrip monitoredTrip = Persistence.monitoredTrip.getById(monitoredTripToDelete.id);
        assertNull(monitoredTrip, "Deleted MonitoredTrip should no longer exist in database (should return as null).");
    }

    @AfterEach
    public void remove() {
        if (monitoredTrip != null) {
            Persistence.monitoredTrip.removeById(monitoredTrip.id);
        }
    }

}
