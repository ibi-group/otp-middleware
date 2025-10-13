package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static org.opentripplanner.middleware.i18n.Message.TRIP_DELAY_ARRIVE;
import static org.opentripplanner.middleware.i18n.Message.TRIP_DELAY_DEPART;
import static org.opentripplanner.middleware.i18n.Message.TRIP_DELAY_EARLY;
import static org.opentripplanner.middleware.i18n.Message.TRIP_DELAY_LATE;

/**
 * Contains information about the type and details of messages to be sent to users about their {@link MonitoredTrip}s.
 */
public class TripMonitorNotification extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(TripMonitorNotification.class);
    public static final String STOPWATCH_ICON = "⏱";

    public NotificationType type;
    public String body;

    /** Getter functions are used by HTML template renderer */
    public String getBody() {
        return body;
    }

    public NotificationType getType() {
        return type;
    }

    /**
     * This no-arg constructor exists to make MongoDB happy.
     */
    public TripMonitorNotification() {
    }

    public TripMonitorNotification(NotificationType type, String body) {
        this.type = type;
        this.body = body;
    }

    /**
     * Create a new notification about a change in the trip's arrival or departure time exceeding a threshold.
     *
     * @param delayInMinutes The delay in minutes (negative values indicate early times).
     * @param startTime The actual departure time of the trip
     * @param arrivalTime The actual arrival time of the trip
     * @param delayType Whether the notification is for an arrival or departure delay
     * @param locale The locale in which to display the message
     */
    public static TripMonitorNotification createDelayNotification(
        long delayInMinutes,
        Date startTime,
        Date arrivalTime,
        NotificationType delayType,
        Locale locale
    ) {
        if (!delayType.isDelayNotification()) {
            LOG.error("Delay notification not permitted for type {}", delayType);
            return null;
        }

        String delayHumanTime = getTimeAdherenceText(delayInMinutes, locale);

        if (delayType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY) {
            return new TripMonitorNotification(
                delayType,
                String.format(
                    Message.TRIP_DELAY_NOTIFICATION_LONG.get(locale),
                    STOPWATCH_ICON,
                    TRIP_DELAY_DEPART.get(locale),
                    delayHumanTime,
                    DateTimeUtils.formatShortDate(startTime, locale),
                    DateTimeUtils.formatShortDate(arrivalTime, locale)
                )
            );
        }

        boolean isArrivalDelay = delayType == NotificationType.ARRIVAL_DELAY;
        return new TripMonitorNotification(
            delayType,
            String.format(
                Message.TRIP_DELAY_NOTIFICATION.get(locale),
                STOPWATCH_ICON,
                (isArrivalDelay ? TRIP_DELAY_ARRIVE : TRIP_DELAY_DEPART).get(locale),
                delayHumanTime,
                DateTimeUtils.formatShortDate(isArrivalDelay ? arrivalTime : startTime, locale)
            )
        );
    }

    /**
     * Create a new notification about the loss of real-time updates.
     */
    public static TripMonitorNotification updatesLostNotification(Locale locale) {
        return new TripMonitorNotification(
            NotificationType.REALTIME_UPDATES_LOST,
            String.format(
                Message.TRIP_DELAY_REALTIME_UPDATES_LOST.get(locale),
                STOPWATCH_ICON
            )
        );
    }

    /**
     * @return A string describing time adherence (e.g. "about on time", "2 minutes early", "5 minutes late").
     */
    private static String getTimeAdherenceText(long delayInMinutes, Locale locale) {
        long absoluteMinutes = Math.abs(delayInMinutes);
        if (absoluteMinutes <= 1) {
            return Message.TRIP_DELAY_ON_TIME.get(locale);
        } else {
            // Delays start at two minutes (plural form).
            String minutesString = String.format(Message.TRIP_DELAY_MINUTES.get(locale), absoluteMinutes);
            return String.format(
                (delayInMinutes > 0 ? TRIP_DELAY_LATE : TRIP_DELAY_EARLY).get(locale),
                minutesString
            );
        }
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

    /**
     * Checks for equality excluding the parent {@link Model} class.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TripMonitorNotification that = (TripMonitorNotification) o;
        return type == that.type && Objects.equals(body, that.body);
    }

    /**
     * Creates a hash code from fields in this class only excluding fields within the parent {@link Model} class.
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, body);
    }
}