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
        TripMonitorAlertSubNotification resolvedAlertsNotification,
        boolean isAllClear
    ) {
        super(
            NotificationType.ALERT_FOUND,
            getBody(newAlertsNotification, resolvedAlertsNotification),
            getBodyShort(newAlertsNotification, resolvedAlertsNotification, isAllClear)
        );
        this.newAlertsNotification = newAlertsNotification;
        this.resolvedAlertsNotification = resolvedAlertsNotification;
    }

    public static TripMonitorAlertNotification createAlertNotification(
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> currentAlerts)
    {
        // Unseen alerts consists of all new alerts that we did not previously track.
        HashSet<LocalizedAlert> unseenAlerts = new HashSet<>(currentAlerts);
        unseenAlerts.removeAll(previousAlerts);
        // Resolved alerts consists of all previous alerts that no longer exist.
        HashSet<LocalizedAlert> resolvedAlerts = new HashSet<>(previousAlerts);
        resolvedAlerts.removeAll(currentAlerts);
        // If there is no change in alerts from previous check, no notification should be created.
        if (unseenAlerts.isEmpty() && resolvedAlerts.isEmpty()) {
            return null;
        }

        TripMonitorAlertSubNotification newAlertsNotification = null;
        TripMonitorAlertSubNotification resolvedAlertsNotification = null;

        // If there are any unseen alerts, include list of these.
        if (!unseenAlerts.isEmpty()) {
            newAlertsNotification = new TripMonitorAlertSubNotification(
                unseenAlerts,
                "New alerts found:",
                String.format("%d new", unseenAlerts.size())
            );
        }
        // If there are any resolved alerts, include list of these.
        boolean isAllClear = false;
        if (!resolvedAlerts.isEmpty()) {
            isAllClear = previousAlerts.size() == resolvedAlerts.size() && unseenAlerts.isEmpty();
            resolvedAlertsNotification = new TripMonitorAlertSubNotification(
                // If all previous alerts were resolved and there are no unseen alerts, send ALL CLEAR.
                resolvedAlerts,
                isAllClear
                    ? "All clear! The following alerts on your itinerary were all resolved:"
                    : "Resolved alerts:",
                isAllClear
                    ? String.format("☑ All clear! %d alert(s) on your itinerary were all resolved.", resolvedAlerts.size())
                    : String.format("%d resolved", resolvedAlerts.size())
            );
        }

        return new TripMonitorAlertNotification(newAlertsNotification, resolvedAlertsNotification, isAllClear);
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

    public static String getBodyShort(
        TripMonitorAlertSubNotification newAlertsNotification,
        TripMonitorAlertSubNotification resolvedAlertsNotification,
        boolean isAllClear
    ) {
        StringBuilder body = new StringBuilder();
        // If there are any unseen or resolved alerts, notify accordingly.
        boolean hasNewAlerts = newAlertsNotification != null && !newAlertsNotification.getAlerts().isEmpty();
        boolean hasResolvedAlerts = resolvedAlertsNotification != null && !resolvedAlertsNotification.getAlerts().isEmpty();
        if (hasResolvedAlerts && isAllClear) {
            body.append(resolvedAlertsNotification.bodyShort);
        } else if (hasNewAlerts || hasResolvedAlerts) {
            body.append("⚠ Your trip has ");
            if (hasNewAlerts) {
                body.append(newAlertsNotification.bodyShort);
            }
            if (hasResolvedAlerts) {
                if (hasNewAlerts) {
                    body.append(", ");
                }
                body.append(resolvedAlertsNotification.bodyShort);
            }
            body.append(" alert(s).");
        }
        return body.toString();
    }
}
