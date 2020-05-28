package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.updateAuthFieldsForUser;
import static org.opentripplanner.middleware.auth.Auth0Users.createNewAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.validateExistingUser;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public class UserController extends ApiController<User> {
    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);
    private static final String TOKEN_PATH = "/fromtoken";

    public UserController(String apiPrefix){
        super(apiPrefix, Persistence.users, "secure/user");
    }

    @Override
    protected ApiEndpoint makeEndPoint(ApiEndpoint baseEndPoint) {
        LOG.info("Registering user/fromtoken path.");

        // Add the user token route before the regular CRUD methods.
        ApiEndpoint modifiedEndpoint = baseEndPoint
            // Get user from token.
            .get(path(ROOT_ROUTE + TOKEN_PATH)
                .withDescription("Retrieves a User entity (based on auth0UserId from request token).")
                .withResponseType(persistence.clazz),
                this::retrieve, JsonUtils::toJson
            )

            // Options response for CORS for the token path
            .options(path(TOKEN_PATH), (req, res) -> "");

        // Add the regular CRUD methods and return to parent.
        return super.makeEndPoint(modifiedEndpoint);
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link User#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    User preCreateHook(User user, Request req) {
        com.auth0.json.mgmt.users.User auth0UserProfile = createNewAuth0User(user, req, this.persistence);
        return updateAuthFieldsForUser(user, auth0UserProfile);
    }

    @Override
    User preUpdateHook(User user, User preExistingUser, Request req) {
        // In order to update a user, the updating user must be authenticated. Because this API is registered under the
        // "public" path, this is not done before the request (see Main class where routes are registered).
        Auth0Connection.checkUser(req);
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        // Additionally, if the user is attempting to update someone else's profile, they must be an admin.
        if (!user.auth0UserId.equals(requestingUser.user_id)) {
            // TODO check that admin has manage user permission.
            if (requestingUser.adminUser == null) {
                logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Must be an admin to update other user accounts.");
            }
        }
        validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(User user, Request req) {
        try {
            deleteAuth0User(user.auth0UserId);
        } catch (Auth0Exception e) {
            logMessageAndHalt(req, 500, "Error deleting user.", e);
            return false;
        }
        return true;
    }

    /**
     * HTTP endpoint to get the User entity from auth0.
     */
    private User retrieve(Request req, Response res) {
        Auth0UserProfile profile = req.attribute("user");
        User result = null;
        String message = "Unknown error.";

        if (profile != null) {
            String auth0UserId = profile.user_id;
            result = persistence.getOneFiltered(eq("auth0UserId", auth0UserId));
            if (result == null) message = String.format("No user with auth0UserID=%s found.", auth0UserId);
        } else {
            message = "Auth0 profile could not be processed.";
        }

        if (result == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, message,null);
        }
        return result;
    }
}
