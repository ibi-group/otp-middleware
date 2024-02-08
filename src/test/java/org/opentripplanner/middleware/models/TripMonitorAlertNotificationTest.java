package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.models.TripMonitorAlertNotification.NEW_ALERT_ICON;
import static org.opentripplanner.middleware.models.TripMonitorAlertNotification.RESOLVED_ALERT_ICON;

class TripMonitorAlertNotificationTest {
    @ParameterizedTest
    @MethodSource("createNewAlertCases")
    void shouldNotifyOnNewAlerts(Locale locale, String alertFormat, String detailText) {
        Set<LocalizedAlert> previousAlerts = Set.of();
        Set<LocalizedAlert> alerts = Set.of(createAlert(), createAlert("Other alert", "Other alert"));

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(
            previousAlerts,
            alerts,
            locale
        );
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format(alertFormat, NEW_ALERT_ICON), notification.body);
        assertEquals(detailText, notification.getNewAlertsNotification().body);
    }

    private static Stream<Arguments> createNewAlertCases() {
        return Stream.of(
            Arguments.of(Locale.ENGLISH, "%s Your trip has 2 new alerts.", "2 new alerts found:"),
            Arguments.of(Locale.FRENCH, "%s Votre trajet comporte 2 nouvelles alertes.", "2 nouvelles alertes trouvées :")
        );
    }

    @ParameterizedTest
    @MethodSource("createResolvedAlertCases")
    void shouldNotifyOnResolvedAlerts(Locale locale, String alertFormat, String detailText) {
        LocalizedAlert remainingAlert = createAlert("Remaining Alert", "Remaining Alert Description");
        Set<LocalizedAlert> previousAlerts = Set.of(remainingAlert, createAlert());
        Set<LocalizedAlert> alerts = Set.of(remainingAlert);

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(
            previousAlerts,
            alerts,
            locale
        );
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format(alertFormat, RESOLVED_ALERT_ICON), notification.body);
        assertEquals(detailText, notification.getResolvedAlertsNotification().body);
    }

    private static Stream<Arguments> createResolvedAlertCases() {
        return Stream.of(
            Arguments.of(Locale.ENGLISH, "%s Your trip has 1 resolved alert.", "1 resolved alert:"),
            Arguments.of(Locale.FRENCH, "%s Votre trajet comporte 1 alerte levée.", "1 alerte levée :")
        );
    }

    @Test
    void shouldNotifyOnAllResolvedAlerts() {
        Set<LocalizedAlert> previousAlerts = Set.of(createAlert());
        Set<LocalizedAlert> alerts = Set.of();

        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(
            previousAlerts,
            alerts,
            Locale.ENGLISH
        );
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(
            String.format("%s All clear! All alerts on your itinerary were all resolved.", RESOLVED_ALERT_ICON),
            notification.body
        );
        assertEquals(
            "All clear! The following alerts on your itinerary were all resolved:",
            notification.getResolvedAlertsNotification().body
        );
    }

    @ParameterizedTest
    @MethodSource("createDisjointAlertCases")
    void shouldNotifyOnDisjointAlerts(Locale locale, String alertFormat) {
        Set<LocalizedAlert> previousAlerts = Set.of(
            createAlert("Trip Other Alert", "Other Alert description"),
            createAlert("Trip Old Alert", "Old Alert description")
        );
        Set<LocalizedAlert> alerts = Set.of(createAlert());

        TripMonitorNotification notification = TripMonitorAlertNotification.createAlertNotification(
            previousAlerts,
            alerts,
            locale
        );
        assertNotNull(notification);
        assertEquals(NotificationType.ALERT_FOUND, notification.type);
        assertEquals(String.format(alertFormat, NEW_ALERT_ICON), notification.body);
    }

    private static Stream<Arguments> createDisjointAlertCases() {
        return Stream.of(
            Arguments.of(Locale.ENGLISH, "%s Your trip has 1 new alert, 2 resolved alerts."),
            Arguments.of(Locale.FRENCH, "%s Votre trajet comporte 1 nouvelle alerte, 2 alertes levées.")
        );
    }

    @Test
    void shouldNotNotifyOnSameAlerts() {
        Date now = new Date();

        // Create two alerts with the same header and description, and effectiveStartDate.
        LocalizedAlert previousAlert = createAlert();
        LocalizedAlert newAlert = createAlert();
        previousAlert.effectiveStartDate = now;
        newAlert.effectiveStartDate = now;

        // Assign different end dates to each alert.
        // This is to reflect the cases where a given alert is "extended",
        // e.g. incidents take longer to resolve than initially planned.
        previousAlert.effectiveEndDate = Date.from(now.toInstant().plus(1, ChronoUnit.HOURS));
        newAlert.effectiveEndDate = Date.from(now.toInstant().plus(2, ChronoUnit.HOURS));

        Set<LocalizedAlert> previousAlerts = Set.of(previousAlert);
        Set<LocalizedAlert> alerts = Set.of(newAlert);

        // These two alerts should be considered the same, and no new alert notifications should be triggered.
        assertNull(TripMonitorAlertNotification.createAlertNotification(previousAlerts, alerts, Locale.ENGLISH));
    }

    private static LocalizedAlert createAlert() {
        return createAlert("Trip Alert", "Alert description");
    }

    private static LocalizedAlert createAlert(String header, String description) {
        LocalizedAlert newAlert = new LocalizedAlert();
        newAlert.alertDescriptionText = description;
        newAlert.alertHeaderText = header;
        return newAlert;
    }
}
