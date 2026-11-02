package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.TrackedJourney;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsRideGwinnettNotifyBusOperatorTest {
    @Test
    void hasNotCanceledNotificationForRoute() throws JsonProcessingException {
        TrackedJourney journey = new TrackedJourney();
        // A request was sent to the bus driver on bus route 10.
        journey.busNotificationMessages.put("Route10", "{\"msg_type\": 1}");
        // A request was sent then canceled to the bus driver on bus route 20.
        journey.busNotificationMessages.put("Route20", "{\"msg_type\": 0}");
        // A request was sent but no response recorded/received.
        journey.busNotificationMessages.put("Route40", null);

        assertTrue(UsRideGwinnettNotifyBusOperator.hasNotSentNotificationForRoute(journey, "Route30"));
        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotCanceledNotificationForRoute(journey, "Route30"));

        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotSentNotificationForRoute(journey, "Route40"));
        assertThrows(IllegalStateException.class, () -> UsRideGwinnettNotifyBusOperator.hasNotCanceledNotificationForRoute(journey, "Route40"));

        assertTrue(UsRideGwinnettNotifyBusOperator.hasNotCanceledNotificationForRoute(journey, "Route10"));

        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotCanceledNotificationForRoute(journey, "Route20"));
    }
}
