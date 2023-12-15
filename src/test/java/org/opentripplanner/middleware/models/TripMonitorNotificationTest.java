package org.opentripplanner.middleware.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

class TripMonitorNotificationTest {
    public static final Locale EN_US_LOCALE = Locale.forLanguageTag("en-US");

    @Test
    void testSortOrderPutsInitialReminderFirst() {
        TripMonitorNotification reminder = new TripMonitorNotification(NotificationType.INITIAL_REMINDER, "reminder");
        Set<TripMonitorNotification> notifications = Set.of(
            new TripMonitorNotification(NotificationType.ALERT_FOUND, "alert"),
            new TripMonitorNotification(NotificationType.DEPARTURE_DELAY, "departure delay"),
            reminder,
            new TripMonitorNotification(NotificationType.ARRIVAL_DELAY, "arrival delay")
        );

        List<TripMonitorNotification> sortedNotifications = notifications.stream()
            .sorted(Comparator.comparingInt(TripMonitorNotification::sortOrder))
            .collect(Collectors.toList());

        assertEquals(reminder, sortedNotifications.get(0));
        for (int i = 1; i < sortedNotifications.size(); i++) {
            assertNotEquals(reminder, sortedNotifications.get(i));
        }
    }

    @Test
    void canCreateInitialReminder() {
        MonitoredTrip trip = makeSampleTrip();
        TripMonitorNotification notification = TripMonitorNotification.createInitialReminderNotification(trip, EN_US_LOCALE);
        assertEquals("Reminder for Sample Trip at 5:44 PM.", notification.body);
    }

    @Test
    void canCreateDelayedTripNotification() {
        MonitoredTrip trip = makeSampleTrip();
        TripMonitorNotification notification = TripMonitorNotification.createDelayNotification(
            10,
            trip.itinerary.startTime,
            NotificationType.ARRIVAL_DELAY,
            EN_US_LOCALE
        );
        assertNotNull(notification);
        assertEquals("Your trip is now predicted to arrive 10 minutes late (at 5:44 PM).", notification.body);
    }

    private static MonitoredTrip makeSampleTrip() {
        MonitoredTrip trip = new MonitoredTrip();
        trip.tripName = "Sample Trip";

        // tripTime is for the OTP query, is included for reference only, and should not be used for notifications.
        trip.tripTime = "13:31";

        // Set a start time for the itinerary, in the ambient/default OTP zone.
        ZonedDateTime startTime = ZonedDateTime.of(2023, 2, 12, 17, 44, 0, 0, DateTimeUtils.getOtpZoneId());

        Itinerary itinerary = new Itinerary();
        itinerary.startTime = Date.from(startTime.toInstant());

        trip.itinerary = itinerary;
        return trip;
    }
}
