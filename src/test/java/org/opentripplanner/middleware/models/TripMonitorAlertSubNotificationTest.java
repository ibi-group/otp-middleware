package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripMonitorAlertSubNotificationTest {

    public static final String NEW_ALERT_NOTIFICATION_TEXT = String.format("New alerts found:%n%n- Alert description");

    @Test
    void canProduceNewAlertPlainText() {
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        TripMonitorAlertSubNotification newAlertSubNotification = new TripMonitorAlertSubNotification(
            "New alerts found:",
            newAlerts
        );
        assertEquals(NEW_ALERT_NOTIFICATION_TEXT, newAlertSubNotification.toString());
    }

    @Test
    void canProduceResolvedAlertPlainText() {
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        TripMonitorAlertSubNotification resolvedAlertSubNotification = new TripMonitorAlertSubNotification(
            "Resolved alerts:",
            newAlerts
        );
        assertEquals(
            String.format("Resolved alerts:%n%n- (RESOLVED) Alert description"),
            resolvedAlertSubNotification.toString(true)
        );
    }

    private static LocalizedAlert createAlert() {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = "Alert description";
        newAlert.alertHeaderText = "Trip Alert";
        return newAlert;
    }
}
