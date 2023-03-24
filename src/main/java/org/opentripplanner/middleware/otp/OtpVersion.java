package org.opentripplanner.middleware.otp;

import org.eclipse.jetty.http.HttpStatus;
import spark.Request;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

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

    /**
     * Converts integer to OTP Version object.
     * @param version Version number as an integer
     * @return OtpVersion object
     */
    private static OtpVersion getOtpVersion(int version) {
        switch(version) {
            case 2:
                return OTP2;
            case 1:
            default:
                return OTP1;
        }
    }

    /**
     * Gets an OTP version from the Spark request header
     * @param request Spark request object
     * @return OtpVersion specified in request
     */
    public static OtpVersion getOtpVersionFromRequest(Request request) {
        try {
            return getOtpVersion(Integer.parseInt(request.queryParamOrDefault("otpVersion", "1")));
        } catch (NumberFormatException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing otpVersion parameter.", e);
            return null;
        }
    }
}