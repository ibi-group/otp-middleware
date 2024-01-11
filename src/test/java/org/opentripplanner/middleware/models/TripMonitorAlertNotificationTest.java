package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.models.TripMonitorAlertSubNotificationTest.NEW_ALERT_NOTIFICATION_TEXT;

class TripMonitorAlertNotificationTest {
    public static final String NEW_ALERT_NOTIFICATION_AND_NEWLINE = NEW_ALERT_NOTIFICATION_TEXT + System.lineSeparator();

    @Test
    void shouldNotifyOnNewAlerts() {
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> alerts = new HashSet<>();
        alerts.add(createAlert());

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(NEW_ALERT_NOTIFICATION_AND_NEWLINE, notification.body);
        assertEquals("⚠ Your trip has 1 new alert(s).", notification.bodyShort);
    }

    @Test
    void shouldNotifyOnResolvedAlerts() {
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> alerts = new HashSet<>();

        LocalizedAlert remainingAlert = createAlert();
        remainingAlert.alertHeaderText = "Remaining Alert";
        remainingAlert.alertDescriptionText = "Remaining Alert Description";
        previousAlerts.add(remainingAlert);
        alerts.add(remainingAlert);

        previousAlerts.add(createAlert());

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
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> alerts = new HashSet<>();
        previousAlerts.add(createAlert());

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
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> alerts = new HashSet<>();
        alerts.add(createAlert());

        LocalizedAlert otherAlert = new LocalizedAlert();
        otherAlert.alertDescriptionText = "Other Alert description";
        otherAlert.alertHeaderText = "Trip Other Alert";
        previousAlerts.add(otherAlert);

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
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> alerts = new HashSet<>();

        previousAlerts.add(createAlert());
        alerts.add(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts);
        assertNull(notification);
    }

    private static LocalizedAlert createAlert() {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = "Alert description";
        newAlert.alertHeaderText = "Trip Alert";
        return newAlert;
    }
}
