package org.opentripplanner.middleware.otp;


import org.apache.commons.lang3.SerializationUtils;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpResponse;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcherResponse.class);

    /** Empty constructor used for testing */
    private OtpDispatcherResponse() {}

    public OtpDispatcherResponse(HttpResponse<String> otpResponse) {
        requestUri = otpResponse.uri();
        responseBody = otpResponse.body();
        statusCode = otpResponse.statusCode();
        LOG.debug("Response from OTP server: {}", toString());
    }

    /**
     * Constructor used only for testing.
     */
    public OtpDispatcherResponse(String otpResponse) {
        requestUri = URI.create("http://test.com");
        responseBody = otpResponse;
        statusCode = 200;
        LOG.debug("Response from OTP server: {}", toString());
    }

    /**
     * Status code. Status code returned with response from an OTP server.
     */
    public int statusCode;

    /** URI (for the OTP server) that the request was sent to */
    public URI requestUri;

    /**
     * Response Body. Response Body returned with response from an OTP server.
     */
    public String responseBody = null;

    /**
     * Response. POJO version of response from an OTP server.
     * Do not persist in case these classes change. This should always be re-instantiated from responseBody if needed.
     */
    public Response getResponse() {
        return JsonUtils.getPOJOFromJSON(responseBody, Response.class);
    }

    public void setResponse(Response response) {
        responseBody = JsonUtils.toJson(response);
    }


    @Override
    public String toString() {
        return "OtpDispatcherResponse{" +
                "statusCode=" + statusCode +
                ", responseBody='" + responseBody + '\'' +
                ", response=" + getResponse() +
                '}';
    }

    @Override
    public OtpDispatcherResponse clone() {
        return SerializationUtils.clone(this);
//        OtpDispatcherResponse clonedObject = new OtpDispatcherResponse();
//        clonedObject.statusCode = this.statusCode;
//        clonedObject.requestUri = URI.create(this.requestUri.toString());
//        clonedObject.responseBody = this.responseBody;
//        return clonedObject;
    }
}
