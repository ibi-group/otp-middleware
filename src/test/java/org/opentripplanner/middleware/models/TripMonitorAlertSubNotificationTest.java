package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TripMonitorAlertSubNotificationTest {
    @Test
    void canProduceNewAlertPlainText() {
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        TripMonitorAlertSubNotification newAlertSubNotification = new TripMonitorAlertSubNotification(
            "New alerts found:",
            newAlerts
        );
        assertEquals(String.format("New alerts found:%n%n- Alert description"), newAlertSubNotification.toString());
    }

    @Test
    void canProduceResolvedAlertPlainText() {
        Set<LocalizedAlert> newAlerts = new HashSet<>();
        newAlerts.add(createAlert());

        TripMonitorAlertSubNotification resolvedAlertSubNotification = new TripMonitorAlertSubNotification(
            "Resolved alerts:",
            newAlerts
        );
        assertEquals(String.format("Resolved alerts:%n%n- (RESOLVED) Alert description"), resolvedAlertSubNotification.toString(true));
    }

    private static LocalizedAlert createAlert() {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = "Alert description";
        newAlert.alertHeaderText = "Trip Alert";
        return newAlert;
    }
}
