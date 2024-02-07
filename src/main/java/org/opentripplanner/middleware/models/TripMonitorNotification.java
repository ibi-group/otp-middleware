package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Locale;

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
     * Create a new notification about a change in the trip's arrival or departure time exceeding a threshold.
     *
     * @param delayInMinutes The delay in minutes (negative values indicate early times).
     * @param targetDatetime The actual arrival or departure of the trip
     * @param delayType Whether the notification is for an arrival or departure delay
     */
    public static TripMonitorNotification createDelayNotification(
        long delayInMinutes,
        Date targetDatetime,
        NotificationType delayType,
        Locale locale
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
                "⏱ Your trip is now predicted to %s %s (at %s).",
                delayType == NotificationType.ARRIVAL_DELAY ? "arrive" : "depart",
                delayHumanTime,
                DateTimeUtils.formatShortDate(targetDatetime, locale)
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
        MonitoredTrip trip, Locale locale
    ) {
        return new TripMonitorNotification(
            NotificationType.INITIAL_REMINDER,
            String.format(Message.TRIP_REMINDER_NOTIFICATION.get(locale),
                trip.tripName,
                DateTimeUtils.formatShortDate(trip.itinerary.startTime, locale)
            )
        );
    }
}
