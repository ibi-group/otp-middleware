package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.Collection;

/**
 * Sub-notification for {@link TripMonitorAlertNotification}.
 */
public class TripMonitorAlertSubNotification extends TripMonitorNotification {
    private final Collection<LocalizedAlert> alerts;

    /** Getter function used by the HTML template renderer */
    public Collection<LocalizedAlert> getAlerts() {
        return alerts;
    }

    public TripMonitorAlertSubNotification(String header, Collection<LocalizedAlert> alerts) {
        super(NotificationType.ALERT_FOUND, header);
        this.alerts = alerts;
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean resolved) {
        StringBuilder result = new StringBuilder();
        result.append(this.body);
        result.append("\n");
        for (LocalizedAlert alert : alerts) {
            result.append("\n- ");
            if (resolved) result.append("(RESOLVED) ");
            result.append(alert.alertDescriptionText);
        }
        return result.toString();
    }
}
