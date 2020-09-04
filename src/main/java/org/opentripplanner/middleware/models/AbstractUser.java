package org.opentripplanner.middleware.models;

import com.auth0.exception.Auth0Exception;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;

/**
 * This is an abstract user class that {@link OtpUser}, {@link AdminUser}, and {@link ApiUser} extend.
 *
 * It provides a place to centralize common fields that all users share (e.g., email) and common methods (such as the
 * authorization check {@link #canBeManagedBy}.
 */
public abstract class AbstractUser extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractUser.class);
    private static final long serialVersionUID = 1L;
    /** Email address for contact. This must be unique in the collection. */
    public String email;
    // TODO: Add personal info (name, phone, etc.)
    /**
     * Auth0 user ID, which we initialize to a random value, but when we link this user up with Auth0 we update this
     * value, so the stored user will contain the value from Auth0 (e.g., "auth0|abcd1234").
     */
    public String auth0UserId = UUID.randomUUID().toString();
    /** Whether a user is also a Data Tools user */
    public boolean isDataToolsUser;
    /**
     * Set of permissions the user has (not persisted in Mongo).
     * TODO: fill out permissions more completely.
     * FIXME: Persist permissions.
     */
    protected Set<Permission> permissions = new HashSet<>();

    /**
     * A requesting user can manage this user object if they are the same user (and not attempting to modify things like
     * permissions) or if the requesting user has permission to manage the entity type.
     */
    @Override
    public boolean canBeManagedBy(Auth0UserProfile user) {
        // If the user is attempting to update someone else's profile, they must be an admin.
        boolean isManagingSelf = this.auth0UserId.equals(user.auth0UserId);
        if (isManagingSelf) {
            return true;
        } else {
            // If not managing self, user must have manage permission.
            for (Permission permission : permissions) {
                if (permission.canManage(this.getClass())) return true;
            }
        }
        // Fallback to Model#userCanManage.
        return super.canBeManagedBy(user);
    }

    @Override
    public boolean delete() {
        if (Auth0Connection.isAuthDisabled()) {
            LOG.warn("Skipping delete user call (Auth is disabled).");
            return true;
        }
        // FIXME: Check that the Auth0 user ID being deleted does not exist as a different child class?
        try {
            // Delete Auth0User.
            deleteAuth0User(this.auth0UserId);
            return true;
        } catch (Auth0Exception e) {
            LOG.error("Could not delete Auth0 user {}.", this.auth0UserId, e);
            return false;
        }
    }
}
