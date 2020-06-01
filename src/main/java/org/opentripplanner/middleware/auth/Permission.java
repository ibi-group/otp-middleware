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

    public boolean canManage(Class<? extends Model> clazz) {
        return this.action.equals(Action.MANAGE) && clazz.equals(this.clazz);
    }

    public enum Action {
        VIEW, MANAGE
    }
}
