package org.opentripplanner.middleware.otp;


import org.apache.commons.lang3.SerializationUtils;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.HashMap;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.TIME_PARAM;

/**
 * An OTP dispatcher response represents the status code and body return from a call to an OTP end point e.g. plan
 */

public class OtpDispatcherResponse implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcherResponse.class);

    /** Empty constructor used for testing */
    public OtpDispatcherResponse() {}

    public OtpDispatcherResponse(HttpResponse<String> otpResponse) {
        requestUri = otpResponse.uri();
        responseBody = otpResponse.body();
        statusCode = otpResponse.statusCode();
        LOG.debug("Response from OTP server: {}", toString());
    }

    /**
     * Constructor used only for testing.
     */
    public OtpDispatcherResponse(String otpResponse, URI requestUri) {
        this.requestUri = requestUri;
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
    public OtpResponse getResponse() {
        return JsonUtils.getPOJOFromJSON(responseBody, OtpResponse.class);
    }

    public void setResponse(OtpResponse response) {
        responseBody = JsonUtils.toJson(response);
    }


    @Override
    public String toString() {
        // Only include the plan response if requestUri.path ends with OTP_PLAN_ENDPOINT.
        // Without this check, we are sending valid responses from non-plan OTP endpoints to Bugsnag as errors.
        String planResponse = requestUri.getPath().endsWith(OTP_PLAN_ENDPOINT)
                ? ", response=" + getResponse()
                : "";

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

    /**
     * @return the first itinerary from the raw OTP response
     * departing the same day as specified in the request date/time parameters, or null otherwise.
     */
    public Itinerary findItineraryDepartingSameDay(boolean tripIsArriveBy) {
        OtpResponse response = this.getResponse();
        TripPlan plan = response.plan;
        HashMap<String, String> reqParams = response.requestParameters;
        if (plan != null && plan.itineraries != null && reqParams != null) {
            String requestDate = reqParams.get(DATE_PARAM);
            String requestTime = reqParams.get(TIME_PARAM);
            if (requestDate != null && requestTime != null) {
                // Get the zone id for this plan for same-day check.
                ZoneId fromZoneId = DateTimeUtils.getOtpZoneId();
                for (Itinerary itinerary : plan.itineraries) {
                    if (ItineraryUtils.isSameDay(
                        itinerary, requestDate, requestTime, fromZoneId, tripIsArriveBy
                    )) {
                        return itinerary;
                    }
                }
            }
        }
        return null;
    }
}
