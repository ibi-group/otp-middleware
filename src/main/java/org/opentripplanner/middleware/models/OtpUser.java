package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses.
 */
public class OtpUser extends AbstractUser {
    public static final String AUTH0_SCOPE = "otp-user";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(OtpUser.class);

    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    /** Whether the phone number has been verified. */
    public boolean isPhoneNumberVerified;

    /**
     * Notification preference for this user
     * ("email", "sms", or "none").
     * TODO: Convert to enum. See http://mongodb.github.io/mongo-java-driver/3.7/bson/pojos/ for guidance.
     */
    public String notificationChannel;

    /**
     * Verified phone number for SMS notifications, in +15551234 format (E.164 format, includes country code, no spaces).
     */
    public String phoneNumber;

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

}
