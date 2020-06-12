package org.opentripplanner.middleware.otp;

import com.mongodb.MongoException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthHeaderPresent;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for getting a plan response from OpenTripPlanner based on the parameters provided by requester. If the
 * user is known and they have given consent store the trip. Pass back to the requester the original response and HTTP
 * status code provided by OpenTripPlanner.
 */
public class OtpRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OtpRequestProcessor.class);
    private static final String OTP_SERVER = getConfigPropertyAsText("OTP_SERVER");
    private static final String OTP_SERVER_PLAN_END_POINT = getConfigPropertyAsText("OTP_SERVER_PLAN_END_POINT");

    /**
     * Obtain and process plan response from OpenTripPlanner. Store the response if consent is given. Handle the
     * process and all exceptions seamlessly so as not to affect the response provided to the requester.
     */
    public static String planning(Request request, spark.Response response) {
        if (OTP_SERVER == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No OTP Server provided, check config.");
            return null;
        }

        String batchId = request.queryParams("batchId");
        if (batchId == null) {
            //TODO place holder for now
            batchId = "-1";
        }

        // attempt to get response from OTP server based on requester's query parameters
        OtpDispatcher otpDispatcher = new OtpDispatcherImpl(OTP_SERVER);
        OtpDispatcherResponse otpDispatcherResponse = otpDispatcher.getPlan(request.queryString(), OTP_SERVER_PLAN_END_POINT);
        if (otpDispatcherResponse == null || otpDispatcherResponse.responseBody == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No response from OTP server.");
            return null;
        }

        // convert plan response into concrete POJOs
        otpDispatcherResponse.response = JsonUtils.getPOJOFromJSON(otpDispatcherResponse.responseBody, Response.class);
        LOG.debug("OTP server response as POJOs: {}", otpDispatcherResponse.response);

        // Dispatch request to OTP and store request/response summary if user elected to store trip history.
        long tripStorageStartTime = System.currentTimeMillis();
        // If the Auth header is present, this indicates that the request was made by a logged in user. This indicates
        // that we should store trip history (but we verify this preference before doing so).
        // FIXME: Determine how much time the Auth checking/request storage takes.
        if (isAuthHeaderPresent(request)) {
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
        // provide response to caller as received from OTP server
        response.type("application/json");
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
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
