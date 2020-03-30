package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;
import java.util.Date;

/**
 * User profile that is attached to an HTTP request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth0UserProfile {
    public String email;
    public boolean email_verified;
    public Date created_at;
    public String name;
    public final String user_id;
    public boolean isAdmin;

    /** Constructor is only used for creating a test user */
    private Auth0UserProfile(String email, String user_id) {
        this.email = email;
        this.user_id = user_id;
        this.created_at = new Date();
        this.email_verified = false;
        this.name = "John Doe";
    }

    /** Create a user profile from the request's JSON web token. Check persistence for stored user */
    public Auth0UserProfile(DecodedJWT jwt) {
        this.user_id = jwt.getClaim("sub").asString();
        String[] roles = jwt.getClaim("https://otp-middleware/roles").asArray(String.class);
        // TODO: This value may need to be stored in config with defaults?
        this.isAdmin = roles != null && Arrays.asList(roles).contains("OTP Admin");
    }

    /**
     * Utility method for creating a test admin (with application-admin permissions) user.
     */
    public static Auth0UserProfile createTestAdminUser() {
        return new Auth0UserProfile("mock@example.com", "user_id:string");
    }
}
