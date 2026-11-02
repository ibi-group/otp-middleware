package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.lang3.StringUtils;

/** Describes a transport mode for OTP GraphQL */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpGraphQLTransportMode {
    /** A mode such as WALK, BUS, BIKE */
    public String mode;


    public static OtpGraphQLTransportMode fromModeString(String modeStr) {
        String[] modeParts = modeStr.split("_");
        OtpGraphQLTransportMode graphQLMode = new OtpGraphQLTransportMode();
        graphQLMode.mode = modeParts[0];
        return graphQLMode;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof OtpGraphQLTransportMode)) return false;
        return sameAs((OtpGraphQLTransportMode) other);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return mode;
    }

    public boolean sameAs(OtpGraphQLTransportMode other) {
        return StringUtils.equals(mode, other.mode);
    }
}
