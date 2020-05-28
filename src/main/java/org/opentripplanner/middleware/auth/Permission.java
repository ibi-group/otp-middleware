package org.opentripplanner.middleware.auth;

import org.opentripplanner.middleware.models.Model;

/**
 * A permission defines the actions that a user can take on a specific entity type.
 */
public class Permission {
    public final Class<? extends Model> clazz;
    public final Action action;

    public Permission(Class<? extends Model> clazz, Action action) {
        this.clazz = clazz;
        this.action = action;
    }

    public enum Action {
        VIEW, MANAGE
    }
}
