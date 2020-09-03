package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(OtpUser.class);

    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    /** Whether the phone number has been verified. (TODO: implement phone verification.) */
    public boolean isPhoneNumberVerified;

    /**
     * Notification preference for this user
     * ("email", "sms", or "none").
     * TODO: Convert to enum. See http://mongodb.github.io/mongo-java-driver/3.7/bson/pojos/ for guidance.
     */
    public String notificationChannel;

    /** Phone number for SMS notifications. */
    public String phoneNumber;

    /** Locations that the user has saved. */
    public List<UserLocation> savedLocations = new ArrayList<>();

    /** Whether to store the user's trip history (user must opt in). */
    public boolean storeTripHistory;

    @JsonIgnore
    public String applicationId;

    @Override
    public boolean delete() {
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
        boolean auth0UserDeleted = super.delete();
        if (auth0UserDeleted) {
            return Persistence.otpUsers.removeById(this.id);
        } else {
            LOG.warn("Aborting user deletion for {}", this.email);
            return false;
        }
    }
}
