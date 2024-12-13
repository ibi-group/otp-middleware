package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.triptracker.TravelerPosition;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

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
    private TripMonitorNotification createTripMonitorNotification(NotificationType notificationType) {
        String body;
        switch (notificationType) {
            case ARRIVED_AND_MODE_CHANGE_NOTIFICATION:
                body = String.format(
                    Message.ARRIVED_AND_MODE_CHANGE_NOTIFICATION.get(observerLocale),
                    travelerName,
                    travelerPosition.expectedLeg.to.name
                );
                break;
            case DEPARTED_NOTIFICATION:
                body = String.format(
                    Message.DEPARTED_NOTIFICATION.get(observerLocale),
                    travelerName,
                    travelerPosition.expectedLeg.from.name
                );
                break;
            case ARRIVED_NOTIFICATION:
                body = String.format(
                    Message.ARRIVED_NOTIFICATION.get(observerLocale),
                    travelerName,
                    travelerPosition.expectedLeg.to.name
                );
                break;
            default:
                body = null;
        }
        return (body != null) ? new TripMonitorNotification(notificationType, body) : null;
    }

    /**
     * Get a list of users that should be notified of a traveler's leg transition.
     */
    public static Set<OtpUser> getLegTransitionNotifyUsers(MonitoredTrip trip) {
        Set<OtpUser> notifyUsers = new HashSet<>();

        if (trip.ownedByPrimary() && trip.companion != null) {
            notifyUsers.add(Persistence.otpUsers.getOneFiltered(eq("email", trip.companion.email)));
        } else if (trip.ownedByCompanion() && trip.primary != null) {
            notifyUsers.add(Persistence.otpUsers.getById(trip.primary.userId));
        }

        trip.observers.forEach(observer -> {
            if (observer.isConfirmed()) {
                notifyUsers.add(Persistence.otpUsers.getOneFiltered(eq("email", observer.email)));
            }
        });

        return notifyUsers;
    }
}