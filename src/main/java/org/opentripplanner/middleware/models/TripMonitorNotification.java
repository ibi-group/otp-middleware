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
    public static final String STOPWATCH_ICON = "⏱";

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
        long absoluteMinutes = Math.abs(delayInMinutes);
        if (absoluteMinutes <= 1) {
            delayHumanTime = Message.TRIP_DELAY_ON_TIME.get(locale);
        } else {
            // Delays start at two minutes (plural form).
            String minutesString = String.format(
                Message.TRIP_DELAY_MINUTES.get(locale),
                delayInMinutes
            );
            if (delayInMinutes > 0) {
                delayHumanTime = String.format(Message.TRIP_DELAY_LATE.get(locale), minutesString);
            } else {
                delayHumanTime = String.format(Message.TRIP_DELAY_EARLY.get(locale), minutesString);
            }
        }

        return new TripMonitorNotification(
            delayType,
            String.format(
                Message.TRIP_DELAY_NOTIFICATION.get(locale),
                STOPWATCH_ICON,
                delayType == NotificationType.ARRIVAL_DELAY
                    ? Message.TRIP_DELAY_ARRIVE.get(locale)
                    : Message.TRIP_DELAY_DEPART.get(locale),
                delayHumanTime,
                DateTimeUtils.formatShortDate(targetDatetime, locale)
            )
        );
    }

    /**
     * Creates a notification that the itinerary was not found on either the current day or any day of the week.
     */
    public static TripMonitorNotification createItineraryNotFoundNotification(
        boolean stillPossibleOnOtherMonitoredDaysOfTheWeek,
        Locale locale
    ) {
        return new TripMonitorNotification(
            NotificationType.ITINERARY_NOT_FOUND,
            stillPossibleOnOtherMonitoredDaysOfTheWeek
                ? Message.TRIP_NOT_FOUND_NOTIFICATION.get(locale)
                : Message.TRIP_NO_LONGER_POSSIBLE_NOTIFICATION.get(locale)
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
