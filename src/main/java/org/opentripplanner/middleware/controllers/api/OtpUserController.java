package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
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
public class OtpUserController extends ApiController<OtpUser> {
    public OtpUserController(String apiPrefix){
        super(apiPrefix, Persistence.otpUsers, "secure/user");
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
}
