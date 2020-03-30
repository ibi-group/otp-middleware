package org.opentripplanner.middleware.models;

import java.io.Serializable;

/**
 * Options associated with users of OTP Admin client.
 */
public class AdminOptions implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Whether user is admin of Admin Dashboard */
    public boolean isAdmin;
    // TODO Determine options.
}
