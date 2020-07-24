package org.opentripplanner.middleware.trip_monitor;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;

import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;

public class TripMonitorTest extends OtpMiddlewareTest {

    @Test
    public void canMonitorTrip() {
        String userId = "123456";
        MonitoredTrip monitoredTrip = createMonitoredTrip(userId);

    }
}
