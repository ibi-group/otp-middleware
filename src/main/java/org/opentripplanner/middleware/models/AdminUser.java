package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;

import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;

/**
 * @schema AdminUser
 *   allOf:
 *    - $ref: '#/components/schemas/AbstractUser'
 */
/**
 * Represents an administrative user of the OTP Admin Dashboard (otp-admin-ui).
 */
public class AdminUser extends AbstractUser {
    // TODO: Add admin-specific fields

    /**
     * Default constructor permits the user to manage all user types.
     * TODO: Add other constructors for varying levels of permissions/different user types?
     */
    public AdminUser() {
        permissions.add(new Permission(AdminUser.class, Permission.Action.MANAGE));
        permissions.add(new Permission(ApiUser.class, Permission.Action.MANAGE));
        permissions.add(new Permission(OtpUser.class, Permission.Action.MANAGE));
    }

    /**
     * Only admin users can create other admin users.
     * TODO: Change to application admin?
     */
    @Override
    public boolean canBeCreatedBy(Auth0UserProfile user) {
        return isUserAdmin(user);
    }
}
