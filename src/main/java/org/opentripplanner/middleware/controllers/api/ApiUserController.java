package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

/**
 * Implementation of the {@link AbstractUserController} for {@link ApiUser}.
 */
public class ApiUserController extends AbstractUserController<ApiUser> {
    public ApiUserController(String apiPrefix){
        super(apiPrefix, Persistence.apiUsers, "secure/application");
    }

    @Override
    protected ApiUser getUserProfile(Auth0UserProfile profile) {
        return profile.apiUser;
    }

    /**
     * Before creating/storing an API user in MongoDB,
     * add the API keys for this user.
     */
    @Override
    ApiUser preCreateHook(ApiUser user, Request req) {
        // TODO: Add the API keys for this user.

        return super.preCreateHook(user, req);
    }
}
