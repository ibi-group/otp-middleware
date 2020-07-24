package org.opentripplanner.middleware.otp;


import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcherResponse.class);

    public OtpDispatcherResponse(HttpResponse<String> otpResponse) {
        responseBody = otpResponse.body();
        statusCode = otpResponse.statusCode();
        LOG.debug("Response from OTP server: {}", toString());
        // convert plan response into concrete POJOs
        response = JsonUtils.getPOJOFromJSON(responseBody, Response.class);
        LOG.debug("OTP server response as POJOs: {}", response);
    }

    /**
     * Status code. Status code returned with response from an OTP server.
     */
    public int statusCode;

    /**
     * Response Body. Response Body returned with response from an OTP server.
     */
    public String responseBody = null;

    /**
     * Response. POJO version of response from an OTP server.
     */
    public Response response = null;


    @Override
    public String toString() {
        return "OtpDispatcherResponse{" +
                "statusCode=" + statusCode +
                ", responseBody='" + responseBody + '\'' +
                ", response=" + response +
                '}';
    }
}
