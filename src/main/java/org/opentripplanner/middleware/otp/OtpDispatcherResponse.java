package org.opentripplanner.middleware.otp;


import org.opentripplanner.middleware.otp.core.api.resource.Response;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse {

    /**
     * Status code. Status code returned with response from an OTP server.
     */
    private final int statusCode;

    /**
     * Response Body. Response Body returned with response from an OTP server.
     */
    private final String responseBody;

    /**
     * Response. POJO version of response from an OTP server.
     */
    private final Response response;


    public OtpDispatcherResponse(int statusCode, String responseBody, Response response) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.response = response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Response getResponse() {
        return response;
    }

    @Override
    public String toString() {
        return "OtpDispatcherResponse{" +
                "statusCode=" + statusCode +
                ", responseBody='" + responseBody + '\'' +
                ", response=" + response +
                '}';
    }
}
