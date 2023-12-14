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

    public final NotificationType type;
    public final String body;

    /** Getter functions are used by HTML template renderer */
    public String getBody() {
        return body;
    }

    public NotificationType getType() {
        return type;
    }

    public TripMonitorNotification(NotificationType type, String body) {
        this.type = type;
        this.body = body;
    }

    /**
     * Basic comparator that sets an arbitrary lower rank for initial reminders,
     * so that they appear before other notifications after list sorting.
     */
    public int sortOrder() {
        return type == NotificationType.INITIAL_REMINDER ? -100 : 0;
    }

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
        if (unseenAlerts.isEmpty() && resolvedAlerts.isEmpty()) {
            return null;
        }
        // Otherwise, construct a notification from the alert sets.
        return new TripMonitorNotification(
            // FIXME: notification type should be determined from alert sets (ALERT_ALL_CLEAR, etc.)?
            NotificationType.ALERT_FOUND,
            bodyFromAlerts(previousAlerts, resolvedAlerts, unseenAlerts)
        );
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

        return new TripMonitorNotification(
            delayType,
            String.format(
                "Your trip is now predicted to %s %s (at %s).",
                delayType == NotificationType.ARRIVAL_DELAY ? "arrive" : "depart",
                delayHumanTime,
                ZonedDateTime
                    .ofInstant(targetDatetime.toInstant(), DateTimeUtils.getOtpZoneId())
                    .format(DateTimeUtils.NOTIFICATION_TIME_FORMATTER)
            )
        );
    }

    /**
     * Creates a notification that the itinerary was not found on either the current day or any day of the week.
     */
    public static TripMonitorNotification createItineraryNotFoundNotification(
        boolean stillPossibleOnOtherMonitoredDaysOfTheWeek
    ) {
        return new TripMonitorNotification(
            NotificationType.ITINERARY_NOT_FOUND,
            stillPossibleOnOtherMonitoredDaysOfTheWeek
                ? "Your itinerary was not found in today's trip planner results. Please check real-time conditions and plan a new trip."
                : "Your itinerary is no longer possible on any monitored day of the week. Please plan and save a new trip."
        );
    }

    /**
     * Creates an initial reminder of the itinerary monitoring.
     */
    public static TripMonitorNotification createInitialReminderNotification(
        MonitoredTrip trip
    ) {
        // TODO: i18n.
        return new TripMonitorNotification(
            NotificationType.INITIAL_REMINDER,
            String.format("Reminder for %s at %s.", trip.tripName, trip.tripTime)
        );
    }

    private static String bodyFromAlerts(
        Set<LocalizedAlert> previousAlerts,
        Set<LocalizedAlert> resolvedAlerts,
        Set<LocalizedAlert> unseenAlerts)
    {
        StringBuilder body = new StringBuilder();
        // If all previous alerts were resolved and there are no unseen alerts, send ALL CLEAR.
        if (previousAlerts.size() == resolvedAlerts.size() && unseenAlerts.isEmpty()) {
            body.append("All clear! The following alerts on your itinerary were all resolved:");
            body.append(listFromAlerts(resolvedAlerts, true));
            // Preempt the final return statement to avoid duplicating
            return body.toString();
        }
        // If there are any unseen alerts, include list of these.
        if (!unseenAlerts.isEmpty()) {
            body.append("\uD83D\uDD14 New alerts found:\n");
            body.append(listFromAlerts(unseenAlerts, false));
        }
        // If there are any resolved alerts, include list of these.
        if (!resolvedAlerts.isEmpty()) {
            if (body.length() > 0) body.append("\n");
            body.append("Resolved alerts:");
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
