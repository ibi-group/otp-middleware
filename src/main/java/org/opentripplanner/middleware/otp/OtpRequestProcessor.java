package org.opentripplanner.middleware.otp;

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
            TripRequest tripRequest = new TripRequest(userId, batchId, request.params(FROM_PLACE), request.params(TO_PLACE), request.queryString());
            Persistence.tripRequest.create(tripRequest);
            TripPlan tripPlan = otpResponse.getPlan();
            TripSummary tripSummary;
            if (tripPlan != null)
                tripSummary = new TripSummary(userId, otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary);
            else
                tripSummary = new TripSummary(userId, otpResponse.getError());
            Persistence.tripSummary.create(tripSummary);
        }

        // provide response to calling UI as received from OTP server
        response.status(otpDispatcherResponse.getStatusCode());
        return otpDispatcherResponse.getResponseBody();
    }
}
