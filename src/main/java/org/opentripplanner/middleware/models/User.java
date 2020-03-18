package org.opentripplanner.middleware.models;

import java.util.UUID;

/**
 * The user represents a user of an OpenTripPlanner instance (typically of the standard OTP UI/otp-react-redux).
 * otp-middleware stores these users and associated information (e.g., home/work locations and other favorites). Users
 * can also opt-in to storing their trip planning requests/responses
 */
public class User extends Model {
    private static final long serialVersionUID = 1L;

    public String email;
    // FIXME: This will eventually reference the Auth0 user ID and not a randomly generated UUID.
    public String auth0UserId = UUID.randomUUID().toString();
    public OpenTripPlannerOptions openTripPlannerOptions;
    public AdminOptions adminOptions;
    /** Whether a user is also a Data Tools user */
    public boolean isDataToolsUser;
}
