package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.trip_monitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.trip_monitor.jobs.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains information about the type and details of messages to be sent to users about their {@link MonitoredTrip}s.
 */
public class TripMonitorNotification extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
    public NotificationType type;
    public String body;

    public static TripMonitorNotification createAlertNotification(
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> newAlerts)
    {
        // Unseen alerts consists of all new alerts that we did not previously track.
        HashSet<LocalizedAlert> unseenAlerts = new HashSet<>(newAlerts);
        unseenAlerts.removeAll(previousAlerts);
        // Resolved alerts consists of all previous alerts that no longer exist.
        HashSet<LocalizedAlert> resolvedAlerts = new HashSet<>(previousAlerts);
        resolvedAlerts.removeAll(newAlerts);
        // If there is no change in alerts from previous check, no notification should be created.
        if (unseenAlerts.size() == 0 && resolvedAlerts.size() == 0) {
            return null;
        }
        // Otherwise, construct a notification from the alert sets.
        TripMonitorNotification notification = new TripMonitorNotification();
        // FIXME: notification type should be determined from alert sets (ALERT_ALL_CLEAR, etc.)?
        notification.type = NotificationType.ALERT_FOUND;
        notification.body = bodyFromAlerts(previousAlerts, resolvedAlerts, unseenAlerts);
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
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> resolvedAlerts,
        Set<LocalizedAlert> unseenAlerts)
    {
        StringBuilder body = new StringBuilder();
        // If all previous alerts were resolved and there are no unseen alerts, send ALL CLEAR.
        if (previousAlerts.size() == resolvedAlerts.size() && unseenAlerts.size() == 0) {
            body.append("All clear! The following alerts on your itinerary were all resolved:");
            body.append(listFromAlerts(resolvedAlerts, true));
            // Preempt the final return statement to avoid duplicating
            return body.toString();
        }
        // If there are any unseen alerts, include list of these.
        if (unseenAlerts.size() > 0) {
            // TODO: Improve message.
            body.append("New alerts found! They are:");
            body.append(listFromAlerts(unseenAlerts, false));
        }
        // If there are any resolved alerts, include list of these.
        if (resolvedAlerts.size() > 0) {
            if (body.length() > 0) body.append("\n");
            // TODO: Improve message.
            body.append("Resolved alerts are:");
            body.append(listFromAlerts(resolvedAlerts, true));
        }
        return body.toString();
    }

    private static String listFromAlerts(Set<LocalizedAlert> alerts, boolean resolved) {
        return alerts.stream()
            .map(alert -> alert.alertDescriptionText)
            .collect(Collectors.joining(
                String.format("\n-%s ", resolved ? " (RESOLVED)" : ""),
                "\n",
                ""
            ));
    }
}
