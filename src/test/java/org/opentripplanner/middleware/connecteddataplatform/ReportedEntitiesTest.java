package org.opentripplanner.middleware.connecteddataplatform;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportedEntitiesTest {
    @Test
    void canGetEntityMap() {
        ReportedEntities reportedEntities = new ReportedEntities();
        reportedEntities.MonitoredTrip = "all";
        reportedEntities.TrackedJourney = "all";

        Map<String, Class<?>> expected = Map.of(
            "MonitoredTrip", MonitoredTrip.class,
            "TrackedJourney", TrackedJourney.class
        );

        assertEquals(expected, reportedEntities.getEntityMap());
    }
}
