package org.opentripplanner.middleware.models;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

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

    // FIXME: should this be stored here? Should we use lastUpdated as a nonce to protect against race conditions in DB
    // writes?
    public Date lastUpdated;
    public Date dateCreated;
}
