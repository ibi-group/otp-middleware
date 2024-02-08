package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Contains alerts information about a {@link MonitoredTrip}.
 */
public class TripMonitorAlertNotification extends TripMonitorNotification {
    public static final String NEW_ALERT_ICON = "⚠";
    public static final String RESOLVED_ALERT_ICON = "☑";
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
        String summaryText
    ) {
        super(NotificationType.ALERT_FOUND, summaryText);
        this.newAlertsNotification = newAlertsNotification;
        this.resolvedAlertsNotification = resolvedAlertsNotification;
    }

    public static TripMonitorAlertNotification createAlertNotification(
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> currentAlerts,
        Locale locale
    ) {
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
                String.format(Message.COLON.get(locale),
                    String.format(
                        unseenAlerts.size() > 1
                            ? Message.TRIP_ALERT_FOUND_PLURAL.get(locale)
                            : Message.TRIP_ALERT_FOUND_SINGULAR.get(locale),
                        formatAlertCount(unseenAlerts, Set.of(), locale)
                    )
                ),
                NEW_ALERT_ICON
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
                    ? Message.TRIP_ALERT_ALL_RESOLVED_WITH_LIST.get(locale)
                    : String.format(Message.COLON.get(locale), formatAlertCount(Set.of(), resolvedAlerts, locale)),
                RESOLVED_ALERT_ICON
            );
        }
        String summary = getSummary(unseenAlerts, resolvedAlerts, isAllClear, locale);
        return new TripMonitorAlertNotification(newAlertsNotification, resolvedAlertsNotification, summary);
    }

    public static String getSummary(
        Set<LocalizedAlert> newAlerts,
        Set<LocalizedAlert> resolvedAlerts,
        boolean isAllClear,
        Locale locale
    ) {
        // If there are any unseen or resolved alerts, notify accordingly.
        boolean hasNewAlerts = !newAlerts.isEmpty();
        boolean hasResolvedAlerts = !resolvedAlerts.isEmpty();
        if (isAllClear) {
            return String.format(Message.TRIP_ALERT_ALL_RESOLVED.get(locale), RESOLVED_ALERT_ICON);
        } else if (hasNewAlerts || hasResolvedAlerts) {
            return String.format(
                Message.TRIP_ALERT_NOTIFICATION.get(locale),
                hasNewAlerts ? NEW_ALERT_ICON : RESOLVED_ALERT_ICON,
                formatAlertCount(newAlerts, resolvedAlerts, locale)
            );
        }
        return "";
    }

    /** Formats alert counts (assuming at least one alert). */
    private static String formatAlertCount(Set<LocalizedAlert> newAlerts, Set<LocalizedAlert> resolvedAlerts, Locale locale) {
        boolean hasNewAlerts = !newAlerts.isEmpty();
        boolean hasResolvedAlerts = !resolvedAlerts.isEmpty();

        String newAlertsText = String.format(
            newAlerts.size() > 1
                ? Message.TRIP_ALERT_NEW_PLURAL.get(locale)
                : Message.TRIP_ALERT_NEW_SINGULAR.get(locale),
            newAlerts.size()
        );
        String resolvedAlertsText = String.format(
            resolvedAlerts.size() > 1
                ? Message.TRIP_ALERT_RESOLVED_PLURAL.get(locale)
                : Message.TRIP_ALERT_RESOLVED_SINGULAR.get(locale),
            resolvedAlerts.size()
        );
        if (hasNewAlerts && hasResolvedAlerts) {
            return String.format(Message.TRIP_ALERT_NEW_AND_RESOLVED.get(locale), newAlertsText, resolvedAlertsText);
        } else if (hasNewAlerts) {
            return newAlertsText;
        } else if (hasResolvedAlerts) {
            return resolvedAlertsText;
        }
        return "";
    }
}
