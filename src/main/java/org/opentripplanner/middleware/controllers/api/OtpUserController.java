package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

/**
 * Implementation of the {@link AbstractUserController} for {@link OtpUser}.
 */
public class OtpUserController extends AbstractUserController<OtpUser> {
    public OtpUserController(String apiPrefix) {
        super(apiPrefix, Persistence.otpUsers, "secure/user");
    }

    @Override
    OtpUser preCreateHook(OtpUser user, Request req) {
        // Check API key and assign user to appropriate collection.
        String apiKey = req.headers("x-api-key");
        ApiUser apiUser = Persistence.apiUsers.getOneFiltered(Filters.eq("apiKeys.value", apiKey));
        user.applicationId = apiUser.id;
        return super.preCreateHook(user, req);
    }

    @Override
    protected OtpUser getUserProfile(Auth0UserProfile profile) {
        return profile.otpUser;
    }
}
