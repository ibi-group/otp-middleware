package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.time.temporal.ChronoUnit;
import java.util.Date;
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
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, newAlerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(NEW_ALERT_NOTIFICATION_AND_NEWLINE, notification.body);
    }

    @Test
    void shouldNotifyOnResolvedAlerts() {
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        previousAlerts.add(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, newAlerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            String.format(
                "All clear! The following alerts on your itinerary were all resolved:%n%n- (RESOLVED) Alert description"
            ),
            notification.body
        );
    }

    @Test
    void shouldNotifyOnDisjointAlerts() {
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        LocalizedAlert otherAlert = new LocalizedAlert();
        otherAlert.alertDescriptionText = "Other Alert description";
        otherAlert.alertHeaderText = "Trip Other Alert";
        previousAlerts.add(otherAlert);

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, newAlerts);
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            NEW_ALERT_NOTIFICATION_AND_NEWLINE +
            String.format("Resolved alerts:%n%n- (RESOLVED) Other Alert description"),
            notification.body
        );
    }

    @Test
    void shouldNotNotifyOnSameAlerts() {
        Set<LocalizedAlert> previousAlerts = new HashSet<>();
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        Date now = new Date();

        // Create two alerts with the same header and description, and effectiveStartDate.
        LocalizedAlert previousAlert = createAlert();
        LocalizedAlert newAlert = createAlert();
        previousAlert.effectiveStartDate = now;
        newAlert.effectiveStartDate = now;

        // Assign different end dates to each alert.
        // This is to reflect the cases where a given alert is "extended",
        // e.g. incidents take longer to resolve than initially planned.
        previousAlert.effectiveEndDate = Date.from(now.toInstant().plus(1, ChronoUnit.HOURS));
        newAlert.effectiveEndDate = Date.from(now.toInstant().plus(2, ChronoUnit.HOURS));

        previousAlerts.add(previousAlert);
        newAlerts.add(newAlert);

        // These two alerts should be considered the same, and no new alert notifications should be triggered.
        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(previousAlerts, newAlerts);
        assertNull(notification);
    }

    private static LocalizedAlert createAlert() {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = "Alert description";
        newAlert.alertHeaderText = "Trip Alert";
        return newAlert;
    }
}
