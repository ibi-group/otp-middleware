package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth0UserProfile {
    public String email;
    public boolean email_verified;
    public String created_at;
    public String name;
    public String user_id;

    public Auth0UserProfile() { }

    public Auth0UserProfile(String email, String user_id) {
        this.email = email;
        this.user_id = user_id;
    }

    public Auth0UserProfile(DecodedJWT jwt) {
        this.user_id = jwt.getClaim("sub").asString();
    }

    /**
     * Utility method for creating a test admin (with application-admin permissions) user.
     */
    public static Auth0UserProfile createTestAdminUser() {
        Auth0UserProfile adminUser = new Auth0UserProfile("mock@example.com", "user_id:string");
        return adminUser;
    }
}
