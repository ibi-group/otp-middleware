package org.opentripplanner.middleware.models;

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
    /** Email address for contact. This must be unique in the collection. */
    public String email;
    // TODO: Add personal info (name, phone, etc.)
    /**
     * Auth0 user ID, which we initialize to a random value, but when we link this user up with Auth0 we update this
     * value, so the stored user
     */
    public String auth0UserId = UUID.randomUUID().toString();
    // TODO: Determine if OTP options should be a part of a separate user class (OtpUser)?
    public OpenTripPlannerOptions openTripPlannerOptions;
    /** Whether a user is also a Data Tools user */
    public boolean isDataToolsUser;
}
