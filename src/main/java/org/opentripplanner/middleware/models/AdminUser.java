package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an administrative user of the OTP Admin Dashboard (otp-admin-ui).
 */
public class AdminUser extends AbstractUser {
    private static final Logger LOG = LoggerFactory.getLogger(AdminUser.class);
    public Set<Subscription> subscriptions = new HashSet<>();
    /**
     * Default constructor permits the user to manage all user types.
     * TODO: Add other constructors for varying levels of permissions/different user types?
     */
    public AdminUser() {
        permissions.add(new Permission(AdminUser.class, Permission.Action.MANAGE));
        permissions.add(new Permission(ApiUser.class, Permission.Action.MANAGE));
        permissions.add(new Permission(OtpUser.class, Permission.Action.MANAGE));
        permissions.add(new Permission(MonitoredTrip.class, Permission.Action.MANAGE));
    }

    /**
     * Only admin users can create other admin users.
     * TODO: Change to application admin?
     */
    @Override
    public boolean canBeCreatedBy(RequestingUser user) {
        return user.isAdmin();
    }

    @Override
    public boolean delete() {
        boolean auth0UserDeleted = super.delete();
        if (auth0UserDeleted) {
            return Persistence.adminUsers.removeById(this.id);
        } else {
            LOG.warn("Aborting user deletion for {}", this.email);
            return false;
        }
    }

    public enum Subscription {
        NEW_ERROR
    }
}
