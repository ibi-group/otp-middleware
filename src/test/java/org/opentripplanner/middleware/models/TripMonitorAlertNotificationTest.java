package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.models.TripMonitorAlertSubNotificationTest.NEW_ALERT_NOTIFICATION_TEXT;

class TripMonitorAlertNotificationTest {
    public static final String NEW_ALERT_NOTIFICATION_AND_NEWLINE = NEW_ALERT_NOTIFICATION_TEXT + System.lineSeparator();

    @Test
    void shouldNotifyOnNewAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of();
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(NEW_ALERT_NOTIFICATION_AND_NEWLINE, notification.body);
        assertEquals("⚠ Your trip has 1 new alert(s).", notification.bodyShort);
    }

    @Test
    void shouldNotifyOnResolvedAlerts() {
        LocalizedAlert remainingAlert = createAlert("Remaining Alert", "Remaining Alert Description");
        Set<LocalizedAlert> previousAlerts = Set.of(remainingAlert, createAlert());
        Set<LocalizedAlert> alerts = Set.of(remainingAlert);

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            String.format(
                "Resolved alerts:%n%n- (RESOLVED) Alert description"
            ),
            notification.body
        );
        assertEquals("⚠ Your trip has 1 resolved alert(s).", notification.bodyShort);
    }

    @Test
    void shouldNotifyOnAllResolvedAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert());
        Set<LocalizedAlert> alerts = Set.of();

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            String.format(
                "All clear! The following alerts on your itinerary were all resolved:%n%n- (RESOLVED) Alert description"
            ),
            notification.body
        );
        assertEquals("☑ All clear! 1 alert(s) on your itinerary were all resolved.", notification.bodyShort);
    }

    @Test
    void shouldNotifyOnDisjointAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert("Trip Other Alert", "Other Alert description"));
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            NEW_ALERT_NOTIFICATION_AND_NEWLINE +
            String.format("Resolved alerts:%n%n- (RESOLVED) Other Alert description"),
            notification.body
        );
        assertEquals("⚠ Your trip has 1 new, 1 resolved alert(s).", notification.bodyShort);
    }

    @Test
    void shouldNotNotifyOnSameAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert());
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNull(notification);
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
