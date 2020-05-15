package org.opentripplanner.middleware.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The user represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses.
 *
 * TODO Update javadoc if this user becomes a base user for other user subclasses (ApiUser, AdminUser, OtpUser).
 */
public class User extends Model {
    private static final long serialVersionUID = 1L;
    /**
     * Auth0 user ID, which we initialize to a random value, but when we link this user up with Auth0 we update this
     * value, so the stored user
     */
    public String auth0UserId = UUID.randomUUID().toString();

    /**
     * Email address for contact. This must be unique in the collection.
     */
    public String email;

    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    /**
     * Whether the email address has been verified.
     * Some identity services, such as Auth0, manage the email verification process and status,
     * so this field is only for reference.
     */
    public boolean isEmailVerified;

    /** Whether the phone number has been verified. */
    public boolean isPhoneNumberVerified;

    /**
     * Notification preference for this user
     * (a combination of "email", "sms").
     * NOTE: This could become an enum array, e.g. https://jira.mongodb.org/browse/JAVA-268.
     */
    public List<String> notificationChannels;

    /** Phone number for SMS notifications. */
    public String phoneNumber;

    /** Locations that the user has searched. */
    public List<UserLocation> recentLocations = new ArrayList<>();

    /** Locations that the user has saved. */
    public List<UserLocation> savedLocations = new ArrayList<>();

    /** Whether to store the user's trip history (user must opt in). */
    public boolean storeTripHistory;

    // TODO: Determine if OTP options should be a part of a separate user class (OtpUser)?
    public OpenTripPlannerOptions openTripPlannerOptions;

    // TODO: determine the fate of this field.
    /** Whether a user is also a Data Tools user */
    public boolean isDataToolsUser;
}
