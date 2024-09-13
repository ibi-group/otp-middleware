package org.opentripplanner.middleware.connecteddataplatform;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportedEntitiesTest {
    @Test
    void canGetEntityMap() {
        ReportedEntities reportedEntities = new ReportedEntities();
        reportedEntities.MonitoredTrip = "all";
        reportedEntities.TrackedJourney = "all";

        Map<String, TypedPersistence<?>> expected = Map.of(
            "MonitoredTrip", Persistence.monitoredTrips,
            "TrackedJourney", Persistence.trackedJourneys
        );

        assertEquals(expected, reportedEntities.getEntityMap());
    }
}
