package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.mongodb.client.model.Filters;
import org.apache.logging.log4j.util.Strings;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opentripplanner.middleware.tripmonitor.TrustedCompanion.invalidateRelatedUsers;
import static org.opentripplanner.middleware.tripmonitor.TrustedCompanion.removeCompanion;
import static org.opentripplanner.middleware.tripmonitor.TrustedCompanion.removeDependent;
import static org.opentripplanner.middleware.tripmonitor.TrustedCompanion.removeObserver;

/**
 * This represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses.
 */
public class OtpUser extends AbstractUser {
    public enum Notification {
        EMAIL, PUSH, SMS, HAPTIC
    }

    public static final String AUTH0_SCOPE = "otp-user";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(OtpUser.class);
    public static final String TRIP_SURVEY_NOTIFICATIONS_FIELD = "tripSurveyNotifications";

    /** Whether the user would like accessible routes by default. */
    public boolean accessibilityRoutingByDefault;

    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    /** Whether the phone number has been verified. */
    public boolean isPhoneNumberVerified;

    /** Mobility profile. */
    public MobilityProfile mobilityProfile;

    /**
     * Notification preferences for this user
     * (EMAIL and/or SMS and/or PUSH).
     */
    public EnumSet<Notification> notificationChannel = EnumSet.noneOf(OtpUser.Notification.class);

    /**
     * Verified phone number for SMS notifications, in +15551234 format (E.164 format, includes country code, no spaces).
     */
    public String phoneNumber;

    /**
     * The date when consent was given by user to receive SMS messages, as required by Twilio,
     * see https://www.twilio.com/docs/verify/sms#consent-and-opt-in-policy.
     * If the user starts the phone verification process, this field is populated
     * just before the verification code is sent.
     */
    @JsonIgnore
    public Date smsConsentDate;

    /**
     * The user's preferred locale, in language tag format
     * e.g. 'en-US', 'fr-FR', 'es-ES', 'zh-CN', etc.
     */
    public String preferredLocale;

    /**
     * Number of push devices associated with user email
     */
    public int pushDevices;

    /** Locations that the user has saved. */
    public List<UserLocation> savedLocations = new ArrayList<>();

    /** Whether to store the user's trip history (user must opt in). */
    public boolean storeTripHistory;

    /** The trail of survey notifications sent for journeys completed by the user. */
    public List<TripSurveyNotification> tripSurveyNotifications = new ArrayList<>();

    @JsonIgnore
    /** If this user was created by an {@link ApiUser}, this parameter will match the {@link ApiUser}'s id */
    public String applicationId;

    /** Companions and observers of this user. */
    public List<RelatedUser> relatedUsers = new ArrayList<>();

    /** A list of users (their ids only) that are dependent on this user. */
    public List<String> dependents = new ArrayList<>();

    /** 
     * List of query params a user's has saved as defaults. Because trip params  
     * often have regional custom mode overrides, we pass a string here.
     * */
    public String userSavedTripDefaults;

    /** This user's name */
    public String name;

    @Override
    public boolean delete() {
        return delete(true);
    }

    public boolean delete(boolean deleteAuth0User) {
        // Attempt to delete Auth0 user if requested and if they exist within Auth0 tenant.
        // Don't cascade delete if deletion of the Auth0 id failed when requested.
        if (deleteAuth0User && Auth0Users.getUserByEmail(email, false) != null) {
            boolean auth0UserDeleted = super.delete();
            if (!auth0UserDeleted) {
                LOG.warn("Aborting user deletion for {}", this.email);
                return false;
            }
        }

        // Delete trip request history (related trip summaries are deleted in TripRequest#delete)
        for (TripRequest request : TripRequest.requestsForUser(this.id)) {
            boolean success = request.delete();
            if (!success) {
                LOG.error("Error deleting user's ({}) trip request {}", this.id, request.id);
                return false;
            }
        }

        // Delete monitored trips.
        if (!deleteOwnTrips()) return false;

        // Delete monitored trips where the user is the primary traveler.
        for (MonitoredTrip trip : MonitoredTrip.tripsForPrimaryTraveler(id)) {
            boolean success = trip.delete();
            if (!success) {
                LOG.error("Error deleting primary user's ({}) monitored trip {}", id, trip.id);
                return false;
            }
        }

        // Delete push devices
        NotificationUtils.deletePushDevices(email);


        // If a related user, invalidate relationship with all dependents.
        for (String userId : dependents) {
            OtpUser dependent = Persistence.otpUsers.getById(userId);
            if (dependent != null && invalidateRelatedUsers(email, dependent.relatedUsers)) {
                Persistence.otpUsers.replace(dependent.id, dependent);
            }
        }

        // If a dependent user, remove relationship with all related users.
        for (RelatedUser relatedUser : relatedUsers) {
            removeDependent(this, relatedUser);
        }

        // If a companion user, invalidate relationship in trips where they are companions and observers.
        // TODO: Should we alert the user who created the trip of the deletion?
        Persistence.monitoredTrips
            .getFiltered(Filters.eq("companion.email", email))
            .forEach(trip -> removeCompanion(this, trip));

        Persistence.monitoredTrips
            .getFiltered(Filters.eq("observers.email", email))
            .forEach(trip -> removeObserver(this, trip));

        return Persistence.otpUsers.removeById(this.id);
    }

    /**
     * Confirm that the requesting user has the required permissions
     */
    @Override
    public boolean canBeManagedBy(RequestingUser requestingUser) {
        if (requestingUser.apiUser != null && requestingUser.apiUser.id.equals(applicationId)) {
            // Otp user was created by this Api user (first or third party).
            return true;
        }
        // Fallback to Model#userCanManage.
        return super.canBeManagedBy(requestingUser);
    }

    /**
     * Get notification channels as comma-separated list in one string
     */
    @JsonGetter(value = "notificationChannel")
    public String getNotificationChannel() {
        return notificationChannel.stream()
            .map(channel -> channel.name().toLowerCase())
            .collect(Collectors.joining(","));
    }

    /**
     * Set notification channels based on comma-separated list in one string
     */
    @JsonSetter(value = "notificationChannel")
    public void setNotificationChannel(String channels) {
        if (channels.isEmpty() || "none".equals(channels)) {
            notificationChannel.clear();
        } else {
            Stream.of(channels.split(","))
                .filter(Objects::nonNull)
                .map(str -> str.trim().toUpperCase())
                .filter(str -> !str.isEmpty())
                .forEach(channel -> {
                    try {
                        notificationChannel.add(Enum.valueOf(OtpUser.Notification.class, channel));
                    } catch (Exception e) {
                        LOG.error("Notification channel \"{}\" is not valid", channel);
                    }
                });
        }
    }

    /** Obtains the last trip survey notification sent. */
    public Optional<TripSurveyNotification> findLastTripSurveyNotificationSent() {
        if (tripSurveyNotifications == null) return Optional.empty();
        return tripSurveyNotifications.stream().max(Comparator.comparingLong(n -> n.timeSent.getTime()));
    }

    /**
     * Use name if available, if not fallback on email (which is a required field).
     */
    @JsonIgnore
    @BsonIgnore
    public String getDisplayedName() {
        return Strings.isBlank(name) ? email.replace("@", " at ") : name;
    }

    /** Obtains a notification with the given id, if available. */
    public Optional<TripSurveyNotification> findNotification(String id) {
        if (tripSurveyNotifications == null || Strings.isBlank(id)) return Optional.empty();
        return tripSurveyNotifications.stream().filter(n -> id.equals(n.id)).findFirst();
    }

    /**
     * Helper method to delete trips where userId is this user's id.
     * @return true if all trips were successfully deleted, false otherwise.
     */
    public boolean deleteOwnTrips() {
        for (MonitoredTrip trip : MonitoredTrip.tripsForUser(this.id)) {
            boolean success = trip.delete();
            if (!success) {
                LOG.error("Error deleting user's ({}) monitored trip {}", this.id, trip.id);
                return false;
            }
        }
        return true;
    }
}
