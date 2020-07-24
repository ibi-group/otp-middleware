package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;

import java.util.List;

public class TripMonitorNotification {
    public NotificationType type;
    public String body;

    public static TripMonitorNotification createAlertNotification(List<LocalizedAlert> alerts) {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = NotificationType.ALERT_FOUND;
        notification.body = bodyFromAlerts(alerts);
        return notification;
    }

    private static String bodyFromAlerts(List<LocalizedAlert> alerts) {
        String body = "Alerts found! They are:";
        for (LocalizedAlert alert : alerts) {
            body += "\n- " + alert.alertDescriptionText;
        }
        return body;
    }
}
