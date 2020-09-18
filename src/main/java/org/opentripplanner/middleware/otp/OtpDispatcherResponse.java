package org.opentripplanner.middleware.otp;


import org.apache.commons.lang3.SerializationUtils;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.Optional;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getZoneIdForCoordinates;
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
     * @return true if the raw OTP response contains an OTP itinerary
     * departing the same day as the request date parameter, false otherwise.
     */
    public Itinerary findItineraryDepartingSameDay() {
        Response response = this.getResponse();
        TripPlan plan = response.plan;
        if (response.requestParameters != null &&
            response.requestParameters.get(DATE_PARAM) != null &&
            plan != null &&
            plan.itineraries != null) {

            // Get the zone id for this plan.
            // TODO: refactor this.
            Optional<ZoneId> fromZoneId = getZoneIdForCoordinates(plan.from.lat, plan.from.lon);
            if (fromZoneId.isEmpty()) {
                String message = String.format(
                    "Could not find coordinate's (lat=%.6f, lon=%.6f) timezone for URI %s",
                    plan.from.lat,
                    plan.from.lon,
                    requestUri
                );
                throw new RuntimeException(message);
            }

            String requestDate = response.requestParameters.get(DATE_PARAM);
            String requestTime = response.requestParameters.get(TIME_PARAM);
            for (Itinerary itinerary : plan.itineraries) {
                if (ItineraryUtils.itineraryDepartsSameDay(itinerary, requestDate, requestTime, fromZoneId.get())) {
                    return itinerary;
                }
            }
        }
        return null;
    }
}
