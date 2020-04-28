package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

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

    public UserController(String apiPrefix){
        super(apiPrefix, Persistence.users, "public/user");
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
}
