package org.opentripplanner.middleware.otp;


import org.opentripplanner.middleware.otp.response.Response;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse {

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
