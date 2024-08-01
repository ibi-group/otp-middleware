package org.opentripplanner.middleware.otp;

/** Describes a transport mode for OTP GraphQL */
public class OtpGraphQLTransportMode {
    /** A mode such as WALK, BUS, BIKE */
    public String mode;

    /** Optional qualifier such as RENT for bike or other vehicle rentals. */
    public String qualifier;

    public OtpGraphQLTransportMode() {
        // Needed for serialization
    }

    /** Creates an instance with a mode and no initial qualifier */
    public OtpGraphQLTransportMode(String mode) {
        this.mode = mode;
    }
}
