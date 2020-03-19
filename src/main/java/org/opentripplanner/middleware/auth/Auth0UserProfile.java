package org.opentripplanner.middleware.auth;

import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth0UserProfile {
    public String email;
    public boolean email_verified;
    public Date created_at;
    public String name;
    public String user_id;

    private Auth0UserProfile(String email, String user_id) {
        this.email = email;
        this.user_id = user_id;
        this.created_at = new Date();
        this.email_verified = false;
        this.name = "John Doe";
    }

    public Auth0UserProfile(User user) {
        this.email = user.getEmail();
        this.user_id = user.getId();
        this.created_at = user.getCreatedAt();
        this.email_verified = user.isEmailVerified();
        this.name = user.getName();
    }

    public Auth0UserProfile(DecodedJWT jwt) {
        this.user_id = jwt.getClaim("sub").asString();
    }

    /**
     * Utility method for creating a test admin (with application-admin permissions) user.
     */
    public static Auth0UserProfile createTestAdminUser() {
        return new Auth0UserProfile("mock@example.com", "user_id:string");
    }
}
