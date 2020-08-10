package org.opentripplanner.middleware.models;

import java.util.ArrayList;
import java.util.List;

/**
 * This represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses.
 */
public class OtpUser extends AbstractUser {
    private static final long serialVersionUID = 1L;

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
     * Phone number for SMS notifications.
     * Must include country code, e.g., +15551234
     */
    public String phoneNumber;

    /** Locations that the user has saved. */
    public List<UserLocation> savedLocations = new ArrayList<>();

    /** Whether to store the user's trip history (user must opt in). */
    public boolean storeTripHistory;
}
