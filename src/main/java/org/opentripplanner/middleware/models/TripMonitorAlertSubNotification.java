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

    private final String icon;

    // Getter functions used by the HTML template renderer.

    public Collection<LocalizedAlert> getAlerts() {
        return alerts;
    }

    /**
     * Emojis/symbols are not converted correctly in email template files,
     * so pass them at runtime instead.
     */
    public String getIcon() { return icon; }

    public TripMonitorAlertSubNotification(Collection<LocalizedAlert> alerts, String emailHeader, String icon) {
        super(NotificationType.ALERT_FOUND, emailHeader);
        this.alerts = alerts == null ? new ArrayList<>() : alerts;
        this.icon = icon;
    }
}
