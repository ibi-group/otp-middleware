package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.opentripplanner.middleware.models.AdminUser;
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
public class AdminUserController extends ApiController<AdminUser> {
    private static final Logger LOG = LoggerFactory.getLogger(AdminUserController.class);

    public AdminUserController(String apiPrefix){
        super(apiPrefix, Persistence.adminUsers, "admin/user");
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link AdminUser#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    AdminUser preCreateHook(AdminUser user, Request req) {
        User auth0UserProfile = createNewAuth0User(user, req, this.persistence);
        return updateAuthFieldsForUser(user, auth0UserProfile);
    }

    @Override
    AdminUser preUpdateHook(AdminUser user, AdminUser preExistingUser, Request req) {
        validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(AdminUser user, Request req) {
        try {
            deleteAuth0User(req, user.auth0UserId);
        } catch (Auth0Exception e) {
            logMessageAndHalt(req, 500, "Error deleting user.", e);
            return false;
        }
        return true;
    }
}
