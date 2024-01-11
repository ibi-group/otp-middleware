package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Sub-notification for {@link TripMonitorAlertNotification}.
 */
public class TripMonitorAlertSubNotification extends TripMonitorNotification {
    private final Collection<LocalizedAlert> alerts;

    // Getter functions used by the HTML template renderer.

    public Collection<LocalizedAlert> getAlerts() {
        return alerts;
    }

    // Emojis/symbols are not converted correctly in email template files,
    // so pass them at runtime instead.

    public String getIcon() {
        return "⚠";
    }

    public String getResolvedIcon() {
        return "☑";
    }

    public TripMonitorAlertSubNotification(Collection<LocalizedAlert> alerts, String emailHeader) {
        super(NotificationType.ALERT_FOUND, emailHeader);
        this.alerts = alerts == null ? new ArrayList<>() : alerts;
    }
}
