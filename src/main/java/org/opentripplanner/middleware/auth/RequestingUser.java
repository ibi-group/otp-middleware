package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

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
            Bson withAuth0UserId = Filters.eq("auth0UserId", auth0UserId);
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
        Bson withAuth0UserId = Filters.eq("auth0UserId", auth0UserId);
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

        if (Auth0Connection.isAuthHeaderPresent(req)) {
            // If the auth header has been provided get the Auth0 user id from it. This is different from normal
            // operation as the parameter will only contain the Auth0 user id and not "Bearer token".
            auth0UserId = req.headers("Authorization");
        }

        return new RequestingUser(auth0UserId);
    }

    /**
     * Determine if the requesting user is a third party API user. A third party API user is classed as a user that has
     * signed up for access to the otp-middleware API. These users are expected to make requests on behalf of the
     * OtpUsers they sign up, via a server application that authenticates via otp-middleware's authenticate
     * endpoint (/api/secure/application/authenticate). OtpUsers created for third party API users enjoy a more limited
     * range of activities (e.g., they cannot receive email/SMS notifications from otp-middleware).
     */
    public boolean isThirdPartyUser() {
        // TODO: Look to enhance api user check. Perhaps define specific field to indicate this?
        return apiUser != null;
    }

    /**
     * Check if the incoming user is an admin user.
     */
    public boolean isAdmin() {
        return adminUser != null;
    }

}
