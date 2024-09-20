package org.opentripplanner.middleware.connecteddataplatform;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.ConfigUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton that holds configuration data about entities to report.
 * Note: Fields are PascalCased to reflect the casing of respective class names.
 */
public class ReportedEntities {
    public static final Map<String, TypedPersistence<?>> persistenceMap = Map.of(
    "MonitoredTrip", Persistence.monitoredTrips,
    "OtpUser", Persistence.otpUsers,
    "TrackedJourney", Persistence.trackedJourneys,
    "TripRequest", Persistence.tripRequests,
    "TripSummary", Persistence.tripSummaries
    );

    public static final Map<String, String> entityMap = Map.copyOf(
        getEntityMap(ConfigUtils.getConfigProperty("CONNECTED_DATA_PLATFORM_REPORTED_ENTITIES"))
    );

    private ReportedEntities() {}

    static Map<String, String> getEntityMap(JsonNode node) {
        if (node == null || node.isEmpty()) return Map.of();

        // Only include keys that are in persistenceMap.
        Map<String, String> map = new HashMap<>();
        for (String key : persistenceMap.keySet()) {
            if (node.has(key)) {
                map.put(key, node.get(key).textValue());
            }
        }

        return map;
    }
}
