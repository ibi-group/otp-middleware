package org.opentripplanner.middleware.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class TripMonitorNotificationTest {
    @Test
    void testSortOrderPutsInitialReminderFirst() {
        TripMonitorNotification reminder = new TripMonitorNotification(NotificationType.INITIAL_REMINDER, "reminder");
        Set<TripMonitorNotification> notifications = Set.of(
            new TripMonitorNotification(NotificationType.ALERT_FOUND, "alert"),
            new TripMonitorNotification(NotificationType.DEPARTURE_DELAY, "departure delay"),
            reminder,
            new TripMonitorNotification(NotificationType.ARRIVAL_DELAY, "arrival delay")
        );

        List<TripMonitorNotification> sortedNotifications = notifications.stream()
            .sorted(Comparator.comparingInt(TripMonitorNotification::sortOrder))
            .collect(Collectors.toList());

        assertEquals(reminder, sortedNotifications.get(0));
        for (int i = 1; i < sortedNotifications.size(); i++) {
            assertNotEquals(reminder, sortedNotifications.get(i));
        }
    }
}
