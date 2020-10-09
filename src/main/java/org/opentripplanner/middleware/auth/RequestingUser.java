package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthHeaderPresent;

/**
 * User profile that is attached to an HTTP request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestingUser {
    public OtpUser otpUser;
    public ApiUser apiUser;
    public AdminUser adminUser;
    public String auth0UserId;

    /**
     * Constructor is only used for creating a test user. If an Auth0 user id is provided check persistence for matching
     * user else create default user.
     */
    private RequestingUser(String auth0UserId) {
        if (auth0UserId == null) {
            this.auth0UserId = "user_id:string";
            otpUser = new OtpUser();
            apiUser = new ApiUser();
            adminUser = new AdminUser();
        } else {
            this.auth0UserId = auth0UserId;
            Bson withAuth0UserId = eq("auth0UserId", auth0UserId);
            otpUser = Persistence.otpUsers.getOneFiltered(withAuth0UserId);
            apiUser = Persistence.apiUsers.getOneFiltered(withAuth0UserId);
            adminUser = Persistence.adminUsers.getOneFiltered(withAuth0UserId);
        }
    }

    /**
     * Create a user profile from the request's JSON web token. Check persistence for stored user.
     */
    public RequestingUser(DecodedJWT jwt) {
        this.auth0UserId = jwt.getClaim("sub").asString();
        Bson withAuth0UserId = eq("auth0UserId", auth0UserId);
        otpUser = Persistence.otpUsers.getOneFiltered(withAuth0UserId);
        apiUser = Persistence.apiUsers.getOneFiltered(withAuth0UserId);
        adminUser = Persistence.adminUsers.getOneFiltered(withAuth0UserId);
    }

    /**
     * Utility method for creating a test user. If a Auth0 user Id is defined within the Authorization header param
     * define test user based on this.
     */
    static RequestingUser createTestUser(Request req) {
        String auth0UserId = null;

        if (isAuthHeaderPresent(req)) {
            // If the auth header has been provided get the Auth0 user id from it. This is different from normal
            // operation as the parameter will only contain the Auth0 user id and not "Bearer token".
            auth0UserId = req.headers("Authorization");
        }

        return new RequestingUser(auth0UserId);
    }
}
