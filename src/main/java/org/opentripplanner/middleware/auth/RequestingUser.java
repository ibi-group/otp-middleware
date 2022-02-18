package org.opentripplanner.middleware.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.CDPUser;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.util.Objects;

/**
 * User profile that is attached to an HTTP request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestingUser {
    private static final Logger LOG = LoggerFactory.getLogger(RequestingUser.class);

    public OtpUser otpUser;
    public ApiUser apiUser;
    public CDPUser cdpUser;
    public AdminUser adminUser;
    public String auth0UserId;

    /**
     * Constructor is only used for creating a test user. If an Auth0 user id is provided check persistence for matching
     * user else create default user.
     */
    private RequestingUser(String auth0UserId, String scope) {
        this.auth0UserId = Objects.requireNonNullElse(auth0UserId, "user_id:string");
        defineUserFromScope(scope, true);
    }

    /**
     * Create a user profile from the request's JSON web token.
     */
    public RequestingUser(DecodedJWT jwt) {
        this.auth0UserId = jwt.getClaim("sub").asString();
        String scope = jwt.getClaim("scope").asString();
        defineUserFromScope(scope, false);
    }

    /**
     * Check persistence for stored user restricted by the scope value provided in the scope claim. It is expected that
     * the scope claim will include only one scope item i.e. 'otp-user', 'api-user' or 'admin-user'. if testing, create
     * a new user else attempt to get a matching user from DB.
     */
    private void defineUserFromScope(String scope, boolean testing) {
        Bson withAuth0UserId = Filters.eq("auth0UserId", auth0UserId);
        if (scope == null || scope.isEmpty()) {
            LOG.error("Required scope claim unavailable");
            return;
        }
        // Define only a single user according to the scope. Note: there is an edge case where a user logging in from
        // the OTP Admin Dashboard may be both an AdminUser and ApiUser, but this code block will force their identity
        // as an AdminUser.
        // TODO: Consider consolidating the user scope fields into a single AbstractUser user field.
        // TODO: move this to a switch block
        if (scope.contains(OtpUser.AUTH0_SCOPE)) {
            otpUser = (testing)
                ? new OtpUser()
                : Persistence.otpUsers.getOneFiltered(withAuth0UserId);
            return;
        }
        if (scope.contains(AdminUser.AUTH0_SCOPE)) {
            adminUser = (testing)
                ? new AdminUser()
                : Persistence.adminUsers.getOneFiltered(withAuth0UserId);
            // Only return at this point if an AdminUser is defined. If not, fall back on defining an ApiUser. If we
            // returned unconditionally, ApiUsers that log in via OTP Admin Dashboard would not be defined below (the
            // final conditional block would never execute.
            if (adminUser != null) return;
        }
        if (scope.contains(ApiUser.AUTH0_SCOPE)) {
            apiUser = (testing)
                ? new ApiUser()
                : Persistence.apiUsers.getOneFiltered(withAuth0UserId);
        }
        if (scope.contains(CDPUser.AUTH0_SCOPE)) {
            cdpUser = (testing)
                    ? new CDPUser()
                    : Persistence.cdpUsers.getOneFiltered(withAuth0UserId);
            return;
        }
        LOG.error("No user type for scope {} is available", scope);
    }

    /**
     * Utility method for creating a test user. If a Auth0 user Id is defined within the Authorization header param
     * define the test user based on this. Restrict authorization to that defined in the scope header param.
     */
    static RequestingUser createTestUser(Request req) {
        // If the auth header has been provided get the Auth0 user id from it. This is different from normal
        // operation as the parameter will only contain the Auth0 user id and not "Bearer token".
        String auth0UserId = req.headers("Authorization");
        // Restrict authorization based on the scope value provided.
        String scope = req.headers("scope");
        return new RequestingUser(auth0UserId, scope);
    }

    /**
     * Determine if the requesting user is a third party API user. A third party API user is classified as a user that has
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

    /**
     * Check if this requesting user can manage the specified entity.
     */
    public boolean canManageEntity(Model model) {
        return model != null && model.canBeManagedBy(this);
    }
}
