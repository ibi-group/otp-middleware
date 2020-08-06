package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.trip_monitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.trip_monitor.jobs.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Contains information about the type and details of messages to be sent to users about their {@link MonitoredTrip}s.
 */
public class TripMonitorNotification extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
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

    public static TripMonitorNotification createDelayNotification(
        long delayInMinutes,
        int delayThreshold,
        NotificationType delayType)
    {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = delayType;
        if (delayType != NotificationType.ARRIVAL_DELAY && delayType != NotificationType.DEPARTURE_DELAY) {
            LOG.error("Delay notification not permitted for type {}", delayType);
            return null;
        }
        notification.body = String.format(
            "The %s time for your itinerary was delayed by %d minutes (your threshold is currently set to %d minutes).",
            delayType == NotificationType.ARRIVAL_DELAY ? "arrival" : "departure",
            delayInMinutes,
            delayThreshold
        );
        return notification;
    }

    public static TripMonitorNotification createItineraryNotFoundNotification() {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = NotificationType.ITINERARY_NOT_FOUND;
        notification.body = "Your itinerary was not found in trip planner results";
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
