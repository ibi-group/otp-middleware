package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;

import static com.mongodb.client.model.Filters.eq;

/**
 * User profile that is attached to an HTTP request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Auth0UserProfile {
    public final OtpUser otpUser;
    public final ApiUser apiUser;
    public final AdminUser adminUser;
    public final String auth0UserId;

    /** Constructor is only used for creating a test user */
    private Auth0UserProfile(String auth0UserId) {
        this.auth0UserId = auth0UserId;
        otpUser = new OtpUser();
        apiUser = new ApiUser();
        adminUser = new AdminUser();
    }

    /** Create a user profile from the request's JSON web token. Check persistence for stored user */
    public Auth0UserProfile(DecodedJWT jwt) {
        this.auth0UserId = jwt.getClaim("sub").asString();
        Bson withAuth0UserId = eq("auth0UserId", auth0UserId);
        otpUser = Persistence.otpUsers.getOneFiltered(withAuth0UserId);
        adminUser = Persistence.adminUsers.getOneFiltered(withAuth0UserId);
        apiUser = Persistence.apiUsers.getOneFiltered(withAuth0UserId);
    }

    /**
     * Utility method for creating a test admin (with application-admin permissions) user.
     */
    public static Auth0UserProfile createTestAdminUser() {
        return new Auth0UserProfile("user_id:string");
    }
}
