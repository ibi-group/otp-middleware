package org.opentripplanner.middleware.otp;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Service;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthHeaderPresent;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for getting a response from OTP based on the parameters provided by the requester. If the target service
 * is of interest the response is intercepted and processed. In all cases, the response from OTP (content and HTTP status)
 * is passed back to the requester.
 */
public class OtpRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OtpRequestProcessor.class);
    /**
     * URI location of the OpenTripPlanner API (e.g., https://otp-server.com/otp). Requests sent to this URI should
     * return OTP version info.
     */
    private static final String OTP_API_ROOT = getConfigPropertyAsText("OTP_API_ROOT");
    /**
     * Location of the plan endpoint for which all requests will be handled by {@link #handlePlanTripResponse}
     */
    private static final String OTP_PLAN_ENDPOINT = getConfigPropertyAsText("OTP_PLAN_ENDPOINT");
    /**
     * Endpoint for the OTP Middleware's OTP proxy
     */
    private static final String OTP_PROXY_ENDPOINT = "/otp";

    /**
     * Register http endpoint with {@link spark.Spark} instance based on the OTP root endpoint. An OTP root endpoint is
     * required to distinguish between OTP and other middleware requests.
     */
    public static void register(Service spark) {
        // available at http://localhost:4567/otp/*
        spark.get(OTP_PROXY_ENDPOINT + "/*", OtpRequestProcessor::proxy);
    }

    /**
     * Responsible for proxying any and all requests made to its HTTP endpoint to OTP. If the target service is of
     * interest (e.g., requests made to the plan trip endpoint are currently logged if the user has consented to storing
     * trip history) the response is intercepted and processed. In all cases, the response from OTP (content and HTTP
     * status) is passed back to the requester.
     */
    private static String proxy(Request request, spark.Response response) {
        if (OTP_API_ROOT == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No OTP Server provided, check config.");
            return null;
        }
        // Get request path intended for OTP API by removing the proxy endpoint (/otp).
        String otpRequestPath = request.uri().replace(OTP_PROXY_ENDPOINT, "");
        // attempt to get response from OTP server based on requester's query parameters
        OtpDispatcherResponse otpDispatcherResponse = OtpDispatcher.sendOtpRequest(request.queryString(), otpRequestPath);
        if (otpDispatcherResponse == null || otpDispatcherResponse.responseBody == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No response from OTP server.");
            return null;
        }

        // If the request path ends with the plan endpoint (e.g., '/plan' or '/default/plan'), process response.
        if (otpRequestPath.endsWith(OTP_PLAN_ENDPOINT)) handlePlanTripResponse(request, otpDispatcherResponse);

        // provide response to requester as received from OTP server
        response.type(APPLICATION_JSON);
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
    }

    /**
     * Process plan response from OTP. Store the response if consent is given. Handle the process and all exceptions
     * seamlessly so as not to affect the response provided to the requester.
     */
    private static void handlePlanTripResponse(Request request, OtpDispatcherResponse otpDispatcherResponse) {

        // If the Auth header is present, this indicates that the request was made by a logged in user. This indicates
        // that we should store trip history (but we verify this preference before doing so).
        if (!isAuthHeaderPresent(request)) {
            LOG.debug("Anonymous user, trip history not stored");
            return;
        }

        String batchId = request.queryParams("batchId");
        if (batchId == null) {
            //TODO place holder for now
            batchId = "-1";
        }

        // Dispatch request to OTP and store request/response summary if user elected to store trip history.
        long tripStorageStartTime = System.currentTimeMillis();

        Auth0Connection.checkUser(request);
        Auth0UserProfile profile = Auth0Connection.getUserFromRequest(request);

        final boolean storeTripHistory = profile != null && profile.otpUser != null && profile.otpUser.storeTripHistory;
        // only save trip details if the user has given consent and a response from OTP is provided
        if (!storeTripHistory) {
            LOG.debug("User does not want trip history stored");
        } else {
            // convert plan response into concrete POJOs
            otpDispatcherResponse.response = JsonUtils.getPOJOFromJSON(otpDispatcherResponse.responseBody, Response.class);
            LOG.debug("OTP server response as POJOs: {}", otpDispatcherResponse.response);

            if (otpDispatcherResponse.response == null) {
                LOG.warn("OTP response is null, cannot save trip history for user!");
            } else {
                TripRequest tripRequest = new TripRequest(profile.otpUser.id, batchId, request.queryParams("fromPlace"),
                    request.queryParams("toPlace"), request.queryString());
                // only save trip summary if the trip request was saved
                boolean tripRequestSaved = Persistence.tripRequests.create(tripRequest);
                if (tripRequestSaved) {
                    TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan,
                        otpDispatcherResponse.response.error, tripRequest.id);
                    Persistence.tripSummaries.create(tripSummary);
                } else {
                    LOG.warn("Unable to save trip request, orphaned trip summary not saved");
                }
            }
        }
        LOG.debug("Trip storage added {} ms", System.currentTimeMillis() - tripStorageStartTime);
    }
}
