package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.persistence.Persistence;

/**
 * Implementation of the {@link AbstractUserController} for {@link AdminUser}.
 */
public class AdminUserController extends AbstractUserController<AdminUser> {
    /**
     * Note: this controller must sit behind the /admin path. This ensures
     * that the requesting user is checked for admin authorization (handled by
     * {@link org.opentripplanner.middleware.auth.Auth0Connection#checkUserIsAdmin}).
     */
    public AdminUserController(String apiPrefix) {
        super(apiPrefix, Persistence.adminUsers, "admin/user");
    }

    @Override
    protected AdminUser getUserProfile(Auth0UserProfile profile) {
        return profile.adminUser;
    }
}
