package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a transit agency member, which has
 * access to the CDP zip files uploaded to S3.
 */
public class CDPUser extends AbstractUser {
    public static final String AUTH0_SCOPE = "cdp-user";
    private static final Logger LOG = LoggerFactory.getLogger(CDPUser.class);

    // FIXME: Move this member to AbstractUser?
    /** The name of this user */
    public String name;

    /** A map to keep track of which s3 files were downloaded at which time */
    public Map<String, Long> S3DownloadTimes = new HashMap<>();

    /**
     * Deletes user from Auth0.
     */
    public boolean delete() {
        boolean auth0UserDeleted = super.delete();
        if (!auth0UserDeleted) {
            LOG.warn("Aborting user deletion for {}", this.email);
            return false;
        }
        return Persistence.cdpUsers.removeById(this.id);
    }
}
