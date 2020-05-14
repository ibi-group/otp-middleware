package org.opentripplanner.middleware.models;

/**
 * Describes a user's commonly used destination, such a home, work, shopping, etc.
 */
public class UserLocation {
    private static final long serialVersionUID = 1L;

    /** The address of this location. */
    public String address;

    /** The icon name for this location (e.g. a FontAwesome icon name). */
    public String icon;

    /** The type/purpose of this location (free form) (e.g. home, work, shopping...) */
    public String type;

}