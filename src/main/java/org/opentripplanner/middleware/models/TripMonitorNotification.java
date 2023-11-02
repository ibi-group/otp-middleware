package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Contains information about the type and details of messages to be sent to users about their {@link MonitoredTrip}s.
 */
public class TripMonitorNotification extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(TripMonitorNotification.class);
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

    /**
     * Create a new notification about a change in the trip's arrival or departure time exceeding a threshold.
     *
     * @param delayInMinutes The delay in minutes (negative values indicate early times).
     * @param targetDatetime The actual arrival or departure of the trip
     * @param delayType Whether the notification is for an arrival or departure delay
     */
    public static TripMonitorNotification createDelayNotification(
        long delayInMinutes,
        Date targetDatetime,
        NotificationType delayType
    ) {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = delayType;
        if (delayType != NotificationType.ARRIVAL_DELAY && delayType != NotificationType.DEPARTURE_DELAY) {
            LOG.error("Delay notification not permitted for type {}", delayType);
            return null;
        }
        String delayHumanTime;
        if (Math.abs(delayInMinutes) <= 1) {
            delayHumanTime = "about on time";
        } else if (delayInMinutes > 0) {
            delayHumanTime = String.format("%d minute%s late", delayInMinutes, delayInMinutes > 1 ? "s" : "");
        } else {
            delayHumanTime = String.format("%d minute%s early", delayInMinutes, delayInMinutes < -1 ? "s" : "");
        }

        notification.body = String.format(
            "Your trip is now predicted to %s %s (at %s).",
            delayType == NotificationType.ARRIVAL_DELAY ? "arrive" : "depart",
            delayHumanTime,
            ZonedDateTime
                .ofInstant(targetDatetime.toInstant(), DateTimeUtils.getOtpZoneId())
                .format(DateTimeUtils.NOTIFICATION_TIME_FORMATTER)
        );
        return notification;
    }

    /**
     * Creates a notification that the itinerary was not found on either the current day or any day of the week.
     */
    public static TripMonitorNotification createItineraryNotFoundNotification(
        boolean stillPossibleOnOtherMonitoredDaysOfTheWeek
    ) {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = NotificationType.ITINERARY_NOT_FOUND;
        notification.body = stillPossibleOnOtherMonitoredDaysOfTheWeek
            ? "Your itinerary was not found in trip planner results for today! Please check realtime conditions and plan a new trip."
            : "Your itinerary is no longer possible any monitored day of the week! Please plan and save a new trip.";
        return notification;
    }

    /**
     * Creates an initial reminder of the itinerary monitoring.
     */
    public static TripMonitorNotification createInitialReminderNotification() {
        TripMonitorNotification notification = new TripMonitorNotification();
        notification.type = NotificationType.INITIAL_REMINDER;
        // TODO: i18n and add itinerary details.
        notification.body = "This is a reminder of your upcoming itinerary";
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
        StringBuilder list = new StringBuilder();
        for (LocalizedAlert alert : alerts) {
            list.append("\n- ");
            if (resolved) list.append("(RESOLVED) ");
            list.append(alert.alertDescriptionText);
        }
        return list.toString();
    }
}
