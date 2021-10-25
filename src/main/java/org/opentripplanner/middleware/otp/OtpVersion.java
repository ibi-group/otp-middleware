package org.opentripplanner.middleware.otp;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Represents an OTP version and associated configuration including its name for pretty printing
 * and URI.
 */
public enum OtpVersion {
    OTP1("OTP_API_ROOT", "OTP 1"),
    OTP2("OTP2_API_ROOT", "OTP 2");

    private final String uri;
    private final String name;

    OtpVersion(String configName, String name) {
        uri = getConfigPropertyAsText(configName);
        this.name = name;
    }
    /**
     * URI location of the OpenTripPlanner API (e.g., https://otp-server.com/otp). Requests sent to this URI should
     * return OTP version info.
     */
    public String uri() {
        return uri;
    }
    public String toString() { return name; }
}