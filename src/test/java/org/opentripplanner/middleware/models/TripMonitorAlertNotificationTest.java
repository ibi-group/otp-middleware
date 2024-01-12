package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.models.TripMonitorAlertNotification.NEW_ALERT_ICON;
import static org.opentripplanner.middleware.models.TripMonitorAlertNotification.RESOLVED_ALERT_ICON;

class TripMonitorAlertNotificationTest {
    @Test
    void shouldNotifyOnNewAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of();
        Set<LocalizedAlert> alerts = Set.of(createAlert(), createAlert("Other alert", "Other alert"));

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format("%s Your trip has 2 new alerts.", NEW_ALERT_ICON), notification.body);
        assertEquals("2 new alerts found:", notification.getNewAlertsNotification().body);
    }

    @Test
    void shouldNotifyOnResolvedAlerts() {
        LocalizedAlert remainingAlert = createAlert("Remaining Alert", "Remaining Alert Description");
        Set<LocalizedAlert> previousAlerts = Set.of(remainingAlert, createAlert());
        Set<LocalizedAlert> alerts = Set.of(remainingAlert);

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format("%s Your trip has 1 resolved alert.", RESOLVED_ALERT_ICON), notification.body);
        assertEquals("1 resolved alert:", notification.getResolvedAlertsNotification().body);
    }

    @Test
    void shouldNotifyOnAllResolvedAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert());
        Set<LocalizedAlert> alerts = Set.of();

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            String.format("%s All clear! All alerts on your itinerary were all resolved.", RESOLVED_ALERT_ICON),
            notification.body
        );
        assertEquals(
            "All clear! The following alerts on your itinerary were all resolved:",
            notification.getResolvedAlertsNotification().body
        );
    }

    @Test
    void shouldNotifyOnDisjointAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert("Trip Other Alert", "Other Alert description"));
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format("%s Your trip has 1 new, 1 resolved alerts.", NEW_ALERT_ICON), notification.body);
    }

    @Test
    void shouldNotNotifyOnSameAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert());
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        assertNull(TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts));
    }

    private static LocalizedAlert createAlert() {
        return createAlert("Trip Alert", "Alert description");
    }

    private static LocalizedAlert createAlert(String header, String description) {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = description;
        newAlert.alertHeaderText = header;
        return newAlert;
    }
}
