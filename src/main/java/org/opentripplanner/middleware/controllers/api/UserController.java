package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.updateAuthFieldsForUser;
import static org.opentripplanner.middleware.auth.Auth0Users.createNewAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.validateExistingUser;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public class UserController extends ApiController<OtpUser> {
    static final String NO_USER_WITH_AUTH0_ID_MESSAGE = "No user with auth0UserID=%s found.";
    private static final String TOKEN_PATH = "/fromtoken";

    public UserController(String apiPrefix){
        super(apiPrefix, Persistence.otpUsers, "secure/user");
    }

    @Override
    protected void buildEndPoint(ApiEndpoint baseEndPoint) {
        LOG.info("Registering user/fromtoken path.");

        // Add the user token route BEFORE the regular CRUD methods
        // (otherwise, /fromtoken requests would be considered
        // by spark as 'GET user with id "fromtoken"', which we don't want).
        ApiEndpoint modifiedEndpoint = baseEndPoint
            // Get user from token.
            .get(path(ROOT_ROUTE + TOKEN_PATH)
                .withDescription("Retrieves a User entity (based on auth0UserId from request token).")
                .withResponseType(persistence.clazz),
                this::getUserFromRequest, JsonUtils::toJson
            )

            // Options response for CORS for the token path
            .options(path(TOKEN_PATH), (req, res) -> "");

        // Add the regular CRUD methods after defining the /fromtoken route.
        super.buildEndPoint(modifiedEndpoint);
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link OtpUser#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    OtpUser preCreateHook(OtpUser user, Request req) {
        com.auth0.json.mgmt.users.User auth0UserProfile = createNewAuth0User(user, req, this.persistence);
        return updateAuthFieldsForUser(user, auth0UserProfile);
    }

    @Override
    OtpUser preUpdateHook(OtpUser user, OtpUser preExistingUser, Request req) {
        validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(OtpUser user, Request req) {
        try {
            deleteAuth0User(user.auth0UserId);
        } catch (Auth0Exception e) {
            logMessageAndHalt(req, 500, "Error deleting user.", e);
            return false;
        }
        return true;
    }

    /**
     * Holds result and message from getUserFromProfile.
     */
    static class UserFromProfileResult {
        public OtpUser user;
        public String message;
    }

    /**
     * HTTP endpoint to get the {@link OtpUser} entity from a {@link Auth0UserProfile}.
     * (Reminder: for endpoints under 'secure', we add a {@link Auth0UserProfile} to request attributes.)
     */
    private OtpUser getUserFromRequest(Request req, Response res) {
        Auth0UserProfile profile = req.attribute("user");
        OtpUser user = profile.otpUser;

        if (user == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404,String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, profile.user_id),null);
        }
        return user;
    }
}
