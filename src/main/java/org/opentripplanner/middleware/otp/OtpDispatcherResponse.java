package org.opentripplanner.middleware.otp;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.http.Header;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.OtpResponseGraphQLWrapper;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcherResponse.class);

    public OtpDispatcherResponse() {
        // Needed for serialization
    }

    public OtpDispatcherResponse(HttpResponseValues otpResponse) {
        requestUri = otpResponse.uri;
        responseBody = otpResponse.responseBody;
        statusCode = otpResponse.status;
        headers = otpResponse.originalClosedResponse.getAllHeaders();
        LOG.debug("Response from OTP server: {}", this);
    }

    /**
     * Constructor used only for testing.
     */
    public OtpDispatcherResponse(String otpResponse, URI requestUri) {
        this.requestUri = requestUri;
        responseBody = otpResponse;
        statusCode = 200;
        LOG.debug("Response from OTP server: {}", this);
    }

    /**
     * Status code. Status code returned with response from an OTP server.
     */
    public int statusCode;

    /**
     * The headers returned by OTP.
     */
    public Header[] headers;

    /** URI (for the OTP server) that the request was sent to */
    public URI requestUri;

    /**
     * Response Body. Response Body returned with response from an OTP server.
     */
    public String responseBody = null;

    /**
     * POJO version of response from an OTP server.
     * Do not persist in case these classes change. This should always be re-instantiated from responseBody if needed.
     */
    public OtpResponse getResponse() throws JsonProcessingException {
        try {
            return JsonUtils.getPOJOFromJSON(responseBody, OtpResponse.class);
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag("Failed to parse OTP response!", responseBody, e);
            throw e;
        }
    }

    /**
     * POJO version of response from an OTP2 server.
     */
    public OtpResponse getOtp2Response() throws JsonProcessingException {
        try {
            return JsonUtils.getPOJOFromJSON(responseBody, OtpResponseGraphQLWrapper.class).data;
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag("Failed to parse OTP2 response!", responseBody, e);
            throw e;
        }
    }

    @Override
    public String toString() {
        // Only include the plan response if requestUri.path ends with OTP_PLAN_ENDPOINT.
        // Without this check, we are sending valid responses from non-plan OTP endpoints to Bugsnag as errors.
        String planResponse = null;
        try {
            planResponse = requestUri.getPath().endsWith(OTP_PLAN_ENDPOINT)
                ? ", response=" + getResponse()
                : "";
        } catch (JsonProcessingException e) {
            LOG.error("Encountered exception while parsing OTP_PLAN_ENDPOINT response", e);
            planResponse = "PARSE_EXCEPTION";
        }

        return "OtpDispatcherResponse{" +
                "statusCode=" + statusCode +
                ", responseBody='" + responseBody + '\'' +
                planResponse +
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
