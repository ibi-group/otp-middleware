package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;

/**
 * Implementation of the {@link AbstractUserController} for {@link OtpUser}.
 */
public class OtpUserController extends AbstractUserController<OtpUser> {
    public OtpUserController(String apiPrefix) {
        super(apiPrefix, Persistence.otpUsers, "secure/user");
    }

    @Override
    protected OtpUser getUserProfile(Auth0UserProfile profile) {
        return profile.otpUser;
    }
}
