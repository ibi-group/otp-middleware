package org.opentripplanner.middleware.otp;

import com.mongodb.MongoException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.otp.core.api.model.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class OtpRequestProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OtpRequestProcessor.class);
    private static final String USER_ID = "userId";
    private static final String BATCH_ID = "batchId";
    private static final String FROM_PLACE = "fromPlace";
    private static final String TO_PLACE = "toPlace";

    public static String planning(Request request, spark.Response response, String otpServer, String endPoint) {
        response.type("application/json");

        if (otpServer == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No OTP Server provided, check config.");
            return null;
        }

        String batchId = request.queryParams(BATCH_ID);
        if (batchId == null) {
            //TODO place holder for now
            batchId = "-1";
        }

        // attempt to get response from OTP server based on UI parameters
        OtpDispatcher otpDispatcher = new OtpDispatcherImpl(otpServer);
        OtpDispatcherResponse otpDispatcherResponse = otpDispatcher.getPlan(request.queryString(), endPoint);
        if (otpDispatcherResponse == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No response from OTP server.");
            return null;
        }

        User user = null;
        String userId = request.queryParams(USER_ID);
        if (userId != null)
            user = Persistence.users.getById(userId);
        else
            //TODO log with logging system?
            LOG.warn("User id not provided, this will be an anonymous request");

        org.opentripplanner.middleware.otp.core.api.resource.Response otpResponse = otpDispatcherResponse.getResponse();
        // only save trip details if user is known and a response from OTP is provided
        if (user != null && otpResponse != null) {
            TripRequest tripRequest = new TripRequest(userId, batchId, request.queryParams(FROM_PLACE), request.queryParams(TO_PLACE), request.queryString());
            TripPlan tripPlan = otpResponse.getPlan();
            TripSummary tripSummary;
            if (tripPlan != null)
                tripSummary = new TripSummary(otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary, tripRequest.id);
            else
                tripSummary = new TripSummary(otpResponse.getError(), tripRequest.id);

            // only save trip summary if the trip request was saved
            if (saveTripRequest(tripRequest))
                saveTripSummary(tripSummary);
            else
                LOG.warn("Unable to save trip request, orphaned trip summary not saved");
        }

        // provide response to calling UI as received from OTP server
        response.status(otpDispatcherResponse.getStatusCode());
        return otpDispatcherResponse.getResponseBody();
    }

    private static boolean saveTripRequest(TripRequest tripRequest) {
        boolean success = true;
        try {
            Persistence.tripRequest.create(tripRequest);
        } catch (MongoException e) {
            success = false;
            LOG.error("Unable to save trip request: " + tripRequest, e);
        }
        return success;
    }

    private static boolean saveTripSummary(TripSummary tripSummary) {
        boolean success = true;
        try {
            Persistence.tripSummary.create(tripSummary);
        } catch (MongoException e) {
            success = false;
            LOG.error("Unable to save trip summary: " + tripSummary, e);
        }
        return success;
    }
}
