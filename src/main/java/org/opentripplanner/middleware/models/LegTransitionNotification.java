package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.triptracker.TravelerPosition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LegTransitionNotification {

    public String travelerName;
    public NotificationType notificationType;
    public TravelerPosition travelerPosition;
    public Locale observerLocale;
    public TripMonitorNotification tripMonitorNotification;

    public LegTransitionNotification(
        String travelerName,
        NotificationType notificationType,
        TravelerPosition travelerPosition,
        Locale observerLocale
    ) {
        this.travelerName = travelerName;
        this.notificationType = notificationType;
        this.travelerPosition = travelerPosition;
        this.observerLocale = observerLocale;
        this.tripMonitorNotification = createTripMonitorNotification(notificationType);
    }

    /**
     * Create {@link TripMonitorNotification} for leg transition based on notification type.
     */
    @Nullable
    public TripMonitorNotification createTripMonitorNotification(NotificationType notificationType) {
        String body;
        switch (notificationType) {
            case MODE_CHANGE_NOTIFICATION:
                body = String.format(
                    Message.MODE_CHANGE_NOTIFICATION.get(travelerPosition.locale),
                    getTravelerName(),
                    travelerPosition.expectedLeg.mode,
                    travelerPosition.nextLeg.mode
                );
                break;
            case DEPARTED_NOTIFICATION:
                body = String.format(
                    Message.DEPARTED_NOTIFICATION.get(observerLocale),
                    getTravelerName(),
                    travelerPosition.expectedLeg.from.name
                );
                break;
            case ARRIVED_NOTIFICATION:
                body = String.format(
                    Message.ARRIVED_NOTIFICATION.get(travelerPosition.locale),
                    getTravelerName(),
                    travelerPosition.expectedLeg.to.name
                );
                break;
            default:
                body = null;
        }
        return (body != null) ? new TripMonitorNotification(notificationType, body) : null;
    }

    /**
     * Get the traveler's name if available, if not provide a generic traveler name.
     */
    private String getTravelerName() {
        if (travelerName != null) {
            return travelerName;
        } else {
            return Message.TRIP_TRAVELER_GENERIC_NAME.get(observerLocale);
        }
    }

    /**
     * Create locale specific notifications.
     */
    public static TripMonitorNotification[] createLegTransitionNotifications(
        List<NotificationType> legTransitionTypes,
        String travelerName,
        TravelerPosition travelerPosition,
        Locale observerLocale
    ) {
        List<TripMonitorNotification> tripMonitorNotifications = new ArrayList<>();
        // Create locale specific notifications.
        for (NotificationType legTransitionType : legTransitionTypes) {
            LegTransitionNotification legTransitionNotification = new LegTransitionNotification(
                travelerName,
                legTransitionType,
                travelerPosition,
                observerLocale
            );
            if (legTransitionNotification.tripMonitorNotification != null) {
                tripMonitorNotifications.add(legTransitionNotification.tripMonitorNotification);
            }
        }
        return tripMonitorNotifications.toArray(new TripMonitorNotification[0]);
    }
}
