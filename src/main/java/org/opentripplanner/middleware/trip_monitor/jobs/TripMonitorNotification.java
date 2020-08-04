package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;

import java.util.Collection;
import java.util.List;

public class TripMonitorNotification {
    public NotificationType type;
    public String body;

    public static TripMonitorNotification createAlertNotification(
        Collection<LocalizedAlert> newAlerts,
        Collection<LocalizedAlert> resolvedAlerts)
    {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = NotificationType.ALERT_FOUND;
        notification.body = bodyFromAlerts(newAlerts, resolvedAlerts);
        return notification;
    }

    private static String bodyFromAlerts(
        Collection<LocalizedAlert> newAlerts,
        Collection<LocalizedAlert> resolvedAlerts)
    {
        StringBuilder body = new StringBuilder();
        if (newAlerts != null && newAlerts.size() > 0) {
            // TODO: Improve message.
            body.append("New alerts found! They are:");
            for (LocalizedAlert alert : newAlerts) {
                body.append("\n- ").append(alert.alertDescriptionText);
            }
        }
        if (resolvedAlerts != null && resolvedAlerts.size() > 0) {
            if (body.length() > 0) body.append("\n");
            // TODO: Improve message.
            body.append("Resolved alerts are:");
            for (LocalizedAlert alert : resolvedAlerts) {
                body.append("\n- ").append(alert.alertDescriptionText);
            }
        }
        return body.toString();
    }
}
