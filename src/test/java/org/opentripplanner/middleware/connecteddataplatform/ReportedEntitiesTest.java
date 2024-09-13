package org.opentripplanner.middleware.connecteddataplatform;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportedEntitiesTest extends OtpMiddlewareTestEnvironment {
    @Test
    void canGetEntityMap() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("MonitoredTrip", "all");
        node.put("TripRequest", "interval");
        node.put("UnknownClass", "abc123");

        Map<String, String> expected = Map.of(
            "MonitoredTrip", "all",
            "TripRequest", "interval"
        );

        assertEquals(expected, ReportedEntities.getEntityMap(node));
    }
}
