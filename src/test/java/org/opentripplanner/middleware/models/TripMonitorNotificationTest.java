package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TripMonitorNotificationTest {
    @ParameterizedTest
    @MethodSource("createInitialReminderCases")
    void canCreateInitialReminder(Locale locale, String text) {
        MonitoredTrip trip = makeSampleTrip();
        TripMonitorNotification notification = TripMonitorNotification.createInitialReminderNotification(trip, locale);
        assertNotNull(notification);
        // JDK 20 uses narrow no-break space U+202F for time format; earlier JDKs just use a space.
        assertThat(notification.body, matchesPattern(text));
    }

    private static Stream<Arguments> createInitialReminderCases() {
        return Stream.of(
            Arguments.of( Locale.ENGLISH, "Reminder for Sample Trip at 5:44[\\u202f ]PM\\."),
            Arguments.of(Locale.FRENCH, "Rappel pour Sample Trip à 17:44.")
        );
    }

    @Test
    void canCreateDelayedTripNotification() {
        MonitoredTrip trip = makeSampleTrip();
        TripMonitorNotification notification = TripMonitorNotification.createDelayNotification(
            10,
            trip.itinerary.startTime,
            NotificationType.ARRIVAL_DELAY,
            Locale.ENGLISH
        );
        assertNotNull(notification);
        // JDK 20 uses narrow no-break space U+202F for time format; earlier JDKs just use a space.
        assertThat(
            notification.body,
            matchesPattern("⏱ Your trip is now predicted to arrive 10 minutes late \\(at 5:44[\\u202f ]PM\\)\\.")
        );
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
