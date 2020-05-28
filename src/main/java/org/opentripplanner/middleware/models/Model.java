package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.Auth0UserProfile;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;

public class Model implements Serializable {
    private static final long serialVersionUID = 1L;

    public Model () {
        // This autogenerates an ID
        // this is OK for dump/restore, because the ID will simply be overridden
        this.id = UUID.randomUUID().toString();
        this.lastUpdated = new Date();
        this.dateCreated = new Date();
    }
    public String id;
    public Date lastUpdated;
    public Date dateCreated;

    /**
     * This is a basic authorization check for any entity to determine if a user can create the entity. By default any
     * user can create any entity. This method should be overridden if there are more restrictions needed.
     */
    public boolean userCanCreate(Auth0UserProfile user) {
        return true;
    }

    /**
     * This is a basic authorization check for any entity to determine if a user can manage it. This method
     * should be overridden in subclasses in order to provide more fine-grained checks.
     */
    public boolean userCanManage(Auth0UserProfile user) {
        // TODO: Check if user has application administrator permission?
        return isUserAdmin(user);
    }
}
