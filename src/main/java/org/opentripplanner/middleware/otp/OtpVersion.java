package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public static OtpVersion intToVersion(int versionAsInt) {
        switch(versionAsInt) {
            case 1:
            default:
                return OTP1;
            case 2:
                return OTP2;
        }
    }

    public static OtpVersion getOtpVersionFromRequest(Request request) {
        try {
            return intToVersion(Integer.parseInt(request.queryParamOrDefault("otpVersion", "1")));
        } catch (NumberFormatException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing otpVersion parameter", e);
            return null;
        }
    }
}