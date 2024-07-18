package org.opentripplanner.middleware.otp;

import org.apache.commons.lang3.StringUtils;

/** Describes a transport mode for OTP GraphQL */
public class OtpGraphQLTransportMode {
    /** A mode such as WALK, BUS, BIKE */
    public String mode;

    /** Optional qualifier such as RENT for bike or other vehicle rentals. */
    public String qualifier;

    public static OtpGraphQLTransportMode fromModeString(String modeStr) {
        String[] modeParts = modeStr.split("_");
        OtpGraphQLTransportMode graphQLMode = new OtpGraphQLTransportMode();
        graphQLMode.mode = modeParts[0];
        if (modeParts.length > 1) {
            graphQLMode.qualifier = modeParts[1];
        }
        return graphQLMode;
    }

    public boolean sameAs(OtpGraphQLTransportMode other) {
        return StringUtils.equals(mode, other.mode) && StringUtils.equals(qualifier, other.qualifier);
    }
}
