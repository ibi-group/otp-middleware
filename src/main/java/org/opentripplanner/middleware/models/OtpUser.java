package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses.
 */
public class OtpUser extends AbstractUser {
    public enum Notification {
        EMAIL, PUSH, SMS
    }

    public static final String AUTH0_SCOPE = "otp-user";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(OtpUser.class);

    /** Whether the user would like accessible routes by default. */
    public boolean accessibilityRoutingByDefault;

    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    /** Whether the user has indicated that their mobility is limited (slower). */
    public boolean isMobilityLimited;

    /** Whether the phone number has been verified. */
    public boolean isPhoneNumberVerified;

    /** User may have indicated zero or more mobility devices. */
    public Collection<String> mobilityDevices;

    /** Compound keyword that controller calculates from mobility and vision values. */
    @JsonIgnore
    public String mobilityMode;

    /** One of "low-vision" "legally blind" "none" */
    public String visionLimitation;

    /**
     * Notification preferences for this user
     * (EMAIL and/or SMS and/or PUSH).
     */
    public EnumSet<OtpUser.Notification> notificationChannel = EnumSet.noneOf(OtpUser.Notification.class);

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

    @JsonIgnore
    /** If this user was created by an {@link ApiUser}, this parameter will match the {@link ApiUser}'s id */
    public String applicationId;

    @Override
    public boolean delete() {
        return delete(true);
    }

    public boolean delete(boolean deleteAuth0User) {
        // Delete trip request history (related trip summaries are deleted in TripRequest#delete)
        for (TripRequest request : TripRequest.requestsForUser(this.id)) {
            boolean success = request.delete();
            if (!success) {
                LOG.error("Error deleting user's ({}) trip request {}", this.id, request.id);
                return false;
            }
        }
        // Delete monitored trips.
        for (MonitoredTrip trip : MonitoredTrip.tripsForUser(this.id)) {
            boolean success = trip.delete();
            if (!success) {
                LOG.error("Error deleting user's ({}) monitored trip {}", this.id, trip.id);
                return false;
            }
        }

        // Only attempt to delete Auth0 user if they exist within Auth0 tenant.
        if (deleteAuth0User && Auth0Users.getUserByEmail(email, false) != null) {
            boolean auth0UserDeleted = super.delete();
            if (!auth0UserDeleted) {
                LOG.warn("Aborting user deletion for {}", this.email);
                return false;
            }
        }

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
}
