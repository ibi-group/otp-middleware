package org.opentripplanner.middleware.otp.graphql;

import org.apache.commons.lang3.StringUtils;

/** Describes a transport mode for OTP GraphQL */
public class TransportMode {
    /** A mode such as WALK, BUS, BIKE */
    public String mode;

    /** Optional qualifier such as RENT for bike or other vehicle rentals. */
    public String qualifier;

    public TransportMode() {
        // Needed for serialization
    }

    /** Creates an instance with a mode and no initial qualifier */
    public TransportMode(String mode) {
        this.mode = mode;
    }

    public String toString() {
        return StringUtils.isBlank(qualifier)
            ? mode
            : String.format("%s_%s", mode, qualifier);
    }
}
