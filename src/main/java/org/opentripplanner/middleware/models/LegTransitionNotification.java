package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.tripmonitor.jobs.NotificationType.MODE_CHANGE_NOTIFICATION;
import static org.opentripplanner.middleware.tripmonitor.jobs.NotificationType.ARRIVED_NOTIFICATION;
import static org.opentripplanner.middleware.tripmonitor.jobs.NotificationType.DEPARTED_NOTIFICATION;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.hasRequiredTransitLeg;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.hasRequiredTripStatus;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.hasRequiredWalkLeg;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isApproachingEndOfLeg;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isAtStartOfLeg;

public class LegTransitionNotification {
    private static final Logger LOG = LoggerFactory.getLogger(LegTransitionNotification.class);

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
            case MODE_CHANGE_NOTIFICATION:
                body = String.format(
                    Message.MODE_CHANGE_NOTIFICATION.get(observerLocale),
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

    /**
     * If a traveler is on the route (not deviated), check for possible leg transition notification.
     */
    public static void checkForLegTransition(
        TripStatus tripStatus,
        TravelerPosition travelerPosition,
        MonitoredTrip trip
    ) {
        if (
            hasRequiredTripStatus(tripStatus) &&
            (hasRequiredWalkLeg(travelerPosition) || hasRequiredTransitLeg(travelerPosition))
        ) {
            NotificationType notificationType = getLegTransitionNotificationType(travelerPosition);
            if (notificationType != null) {
                try {
                    new CheckMonitoredTrip(trip).processLegTransition(notificationType, travelerPosition);
                } catch (CloneNotSupportedException e) {
                    LOG.error("Error encountered while checking leg transition.", e);
                }
            }
        }
    }

    /**
     * Depending on the traveler's proximity to the start/end of a leg, return the appropriate notification type.
     */
    private static NotificationType getLegTransitionNotificationType(TravelerPosition travelerPosition) {
        if (isAtStartOfLeg(travelerPosition)) {
            return DEPARTED_NOTIFICATION;
        } else if (isApproachingEndOfLeg(travelerPosition)) {
            if (hasModeChanged(travelerPosition)) {
                return MODE_CHANGE_NOTIFICATION;
            }
            return ARRIVED_NOTIFICATION;
        }
        return null;
    }

    /**
     * The traveler is at the end of the current leg and the mode has changed between this and the next leg.
     */
    private static boolean hasModeChanged(TravelerPosition travelerPosition) {
        Leg nextLeg = travelerPosition.nextLeg;
        Leg expectedLeg = travelerPosition.expectedLeg;
        return
            isApproachingEndOfLeg(travelerPosition) &&
            nextLeg != null &&
            !nextLeg.mode.equalsIgnoreCase(expectedLeg.mode);
    }
}