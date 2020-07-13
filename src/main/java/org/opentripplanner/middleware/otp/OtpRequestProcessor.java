package org.opentripplanner.middleware.otp;

import com.mongodb.MongoException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Service;

import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthHeaderPresent;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for getting a response from OTP based on the parameters provided by the requester. If the target service
 * is of interest the response is intercepted and processed. In all cases, the response from OTP (content and HTTP status)
 * is passed back to the requester.
 */
public class OtpRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OtpRequestProcessor.class);
    private static final String OTP_SERVER = getConfigPropertyAsText("OTP_SERVER");
    private static final String OTP_ROOT_ENDPOINT = getConfigPropertyAsText("OTP_ROOT_ENDPOINT");
    private static final String OTP_PLAN_ENDPOINT = getConfigPropertyAsText("OTP_PLAN_ENDPOINT");

    /**
     * Register http endpoint with {@link spark.Spark} instance based on the OTP root endpoint. An OTP root endpoint is
     * required to distinguish between OTP and other middleware requests.
     */
    public static void register(Service spark) {
        // available at (depending on config) http://localhost:4567/otp/*
        spark.get(OTP_ROOT_ENDPOINT + "/*", OtpRequestProcessor::proxy);
    }

    /**
     * Inspect all requests that are made to OTP. If the request is of interest (for instance plan response) intercept
     * the response from OTP and process accordingly. In all cases, pass the response back to the requester.
     */
    private static String proxy(Request request, spark.Response response) {
        if (OTP_SERVER == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No OTP Server provided, check config.");
            return null;
        }

        // attempt to get response from OTP server based on requester's query parameters
        OtpDispatcherResponse otpDispatcherResponse = OtpDispatcher.serviceRequest(request.queryString(), request.uri());
        if (otpDispatcherResponse == null || otpDispatcherResponse.responseBody == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No response from OTP server.");
            return null;
        }

        String targetService = extractTargetService(request.uri());
        // if the target service is plan, process response
        if (targetService.equalsIgnoreCase(OTP_PLAN_ENDPOINT)) plan(request, otpDispatcherResponse);

        // provide response to requester as received from OTP server
        response.type("application/json");
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
    }

    /**
     * Extract from the requesting URI the OTP target service e.g. 'routers/default/plan'. The assumption is that the
     * target service will be the remaining part of the URL after the OTP root endpoint has been removed.
     *
     * The following example is based on calls being made to the FDOT OTP server:
     *
     * Middleware call: http://otp-middleware.com/otp/routers/default/plan
     * FDOT OTP call: https://fdot-otp-server.com/otp/routers/default/plan
     * OTP root endpoint: /otp/
     * Target service: routers/default/plan
     */
    private static String extractTargetService(String uri) {
        return uri.replace(OTP_ROOT_ENDPOINT, "");
    }

    /**
     * Process plan response from OTP. Store the response if consent is given. Handle the process and all exceptions
     * seamlessly so as not to affect the response provided to the requester.
     */
    private static void plan(Request request, OtpDispatcherResponse otpDispatcherResponse) {

        // If the Auth header is present, this indicates that the request was made by a logged in user. This indicates
        // that we should store trip history (but we verify this preference before doing so).
        if (!isAuthHeaderPresent(request)) {
            return;
        }

        String batchId = request.queryParams("batchId");
        if (batchId == null) {
            //TODO place holder for now
            batchId = "-1";
        }

        // convert plan response into concrete POJOs
        otpDispatcherResponse.response = JsonUtils.getPOJOFromJSON(otpDispatcherResponse.responseBody, Response.class);
        LOG.debug("OTP server response as POJOs: {}", otpDispatcherResponse.response);

        // Dispatch request to OTP and store request/response summary if user elected to store trip history.
        long tripStorageStartTime = System.currentTimeMillis();

        Auth0Connection.checkUser(request);
        Auth0UserProfile profile = Auth0Connection.getUserFromRequest(request);

        final boolean storeTripHistory = profile != null && profile.otpUser != null && profile.otpUser.storeTripHistory;
        // only save trip details if the user has given consent and a response from OTP is provided
        if (!storeTripHistory) {
            LOG.debug("Anonymous user or user does not want trip history stored");
        } else if (otpDispatcherResponse.response == null) {
            LOG.warn("OTP response is null, cannot save trip history for user!");
        } else {
            TripRequest tripRequest = new TripRequest(profile.otpUser.id, batchId, request.queryParams("fromPlace"),
                request.queryParams("toPlace"), request.queryString());
            // only save trip summary if the trip request was saved
            if (saveTripRequest(tripRequest)) {
                TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan,
                    otpDispatcherResponse.response.error, tripRequest.id);
                saveTripSummary(tripSummary);
            } else {
                LOG.warn("Unable to save trip request, orphaned trip summary not saved");
            }
        }
        LOG.debug("Trip storage added {} ms", System.currentTimeMillis() - tripStorageStartTime);
    }

    /**
     * Save trip request to Mongo and return if successful
     */
    private static boolean saveTripRequest(TripRequest tripRequest) {
        boolean success = true;
        try {
            Persistence.tripRequests.create(tripRequest);
        } catch (MongoException e) {
            success = false;
            LOG.error("Unable to save trip request: " + tripRequest, e);
        }
        return success;
    }

    /**
     * Save trip summary to Mongo
     */
    private static void saveTripSummary(TripSummary tripSummary) {
        try {
            Persistence.tripSummaries.create(tripSummary);
        } catch (MongoException e) {
            LOG.error("Unable to save trip summary: " + tripSummary, e);
        }
    }
}
