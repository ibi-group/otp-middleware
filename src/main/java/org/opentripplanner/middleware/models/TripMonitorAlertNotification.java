package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains alerts information about a {@link MonitoredTrip}.
 */
public class TripMonitorAlertNotification extends TripMonitorNotification {
    private final TripMonitorAlertSubNotification newAlertsNotification;

    private final TripMonitorAlertSubNotification resolvedAlertsNotification;

    /** Getter functions are used by the HTML template renderer */
    public TripMonitorAlertSubNotification getNewAlertsNotification() {
        return newAlertsNotification;
    }

    public TripMonitorAlertSubNotification getResolvedAlertsNotification() {
        return resolvedAlertsNotification;
    }

    public TripMonitorAlertNotification(
        TripMonitorAlertSubNotification newAlertsNotification,
        TripMonitorAlertSubNotification resolvedAlertsNotification
    ) {
        super(NotificationType.ALERT_FOUND, getBody(newAlertsNotification, resolvedAlertsNotification));
        this.newAlertsNotification = newAlertsNotification;
        this.resolvedAlertsNotification = resolvedAlertsNotification;
    }

    public static TripMonitorAlertNotification createAlertNotification(
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
        if (unseenAlerts.isEmpty() && resolvedAlerts.isEmpty()) {
            return null;
        }

        TripMonitorAlertSubNotification newAlertsNotification = null;
        TripMonitorAlertSubNotification resolvedAlertsNotification = null;

        // If there are any unseen alerts, include list of these.
        if (!unseenAlerts.isEmpty()) {
            newAlertsNotification = new TripMonitorAlertSubNotification(
                "New alerts found:",
                unseenAlerts
            );
        }
        // If there are any resolved alerts, include list of these.
        if (!resolvedAlerts.isEmpty()) {
            resolvedAlertsNotification = new TripMonitorAlertSubNotification(
                // If all previous alerts were resolved and there are no unseen alerts, send ALL CLEAR.
                previousAlerts.size() == resolvedAlerts.size() && unseenAlerts.isEmpty()
                    ? "All clear! The following alerts on your itinerary were all resolved:"
                    : "Resolved alerts:",
                resolvedAlerts
            );
        }

        return new TripMonitorAlertNotification(newAlertsNotification, resolvedAlertsNotification);
    }

    /**
     * Basic text formatting of the alert notification.
     */
    public static String getBody(
        TripMonitorAlertSubNotification newAlertsNotification,
        TripMonitorAlertSubNotification resolvedAlertsNotification
    ) {
        StringBuilder body = new StringBuilder();
        if (newAlertsNotification != null) {
            body.append(newAlertsNotification);
            body.append(System.lineSeparator());
        }
        if (resolvedAlertsNotification != null) {
            body.append(resolvedAlertsNotification.toString(true));
        }
        return body.toString();
    }

    private static String bodyShortFromAlerts(
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> resolvedAlerts,
        Set<LocalizedAlert> unseenAlerts)
    {
        StringBuilder body = new StringBuilder();
        // If all previous alerts were resolved and there are no unseen alerts, send ALL CLEAR.
        if (previousAlerts.size() == resolvedAlerts.size() && unseenAlerts.isEmpty()) {
            body.append(String.format("☑ All clear! %d alerts on your itinerary were all resolved.", resolvedAlerts.size()));
            // Preempt the final return statement to avoid duplicating
            return body.toString();
        }
        // If there are any unseen or resolved alerts, notify accordingly.
        if (!unseenAlerts.isEmpty() || !resolvedAlerts.isEmpty()) {
            body.append("⚠ Your trip has ");
            if (!unseenAlerts.isEmpty()) {
                body.append(String.format("%d new", unseenAlerts.size()));
            }
            if (!resolvedAlerts.isEmpty()) {
                if (!unseenAlerts.isEmpty()) {
                    body.append(", ");
                }
                body.append(String.format("%d resolved", resolvedAlerts.size()));
            }
            body.append(" alerts.");
        }
        return body.toString();
    }

    private static String listFromAlerts(Set<LocalizedAlert> alerts, boolean resolved) {
        StringBuilder list = new StringBuilder();
        for (LocalizedAlert alert : alerts) {
            list.append("\n- ");
            if (resolved) list.append("(RESOLVED) ");
            list.append(alert.alertDescriptionText);
        }
        return list.toString();
    }

}
