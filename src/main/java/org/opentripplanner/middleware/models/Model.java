package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class Model implements Serializable {
    private static final long serialVersionUID = 1L;

    public Model () {
        // This autogenerates an ID
        // this is OK for dump/restore, because the ID will simply be overridden
        this.id = UUID.randomUUID().toString();
        this.lastUpdated = DateTimeUtils.nowAsDate();
        this.dateCreated = DateTimeUtils.nowAsDate();
    }
    public String id;
    public Date lastUpdated;
    public Date dateCreated;

    /**
     * This is a basic authorization check for any entity to determine if a user can create the entity. By default any
     * user can create any entity. This method should be overridden if there are more restrictions needed.
     */
    public boolean canBeCreatedBy(RequestingUser user) {
        return true;
    }

    /**
     * This is a basic authorization check for any entity to determine if a user can manage it. This method
     * should be overridden in subclasses in order to provide more fine-grained checks.
     */
    public boolean canBeManagedBy(RequestingUser user) {
        // TODO: Check if user has application administrator permission?
        return user.isAdmin();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Model model = (Model) o;
        return id.equals(model.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public boolean delete() throws Exception {
        return false;
    }
}
