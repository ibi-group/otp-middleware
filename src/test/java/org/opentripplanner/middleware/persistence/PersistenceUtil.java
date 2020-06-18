package org.opentripplanner.middleware.persistence;

import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to aid with creating and storing objects in Mongo.
 */
public class PersistenceUtil {

    private static final String BATCH_ID = "783726";
    private static final String TRIP_REQUEST_ID = "59382";

    private static Response PLAN_RESPONSE = null;
    private static Response PLAN_ERROR_RESPONSE = null;


    /**
     * Create user and store in database.
     */
    public static OtpUser createUser(String email) {
        OtpUser user = new OtpUser();
        user.email = email;
        Persistence.otpUsers.create(user);
        return user;
    }

    /**
     * Create trip request and store in database.
     */
    public static TripRequest createTripRequest(String userId) {
        String fromPlace = "28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034";
        String toPlace = "28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759";
        String queryParams = "arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";
        TripRequest tripRequest = new TripRequest(userId, BATCH_ID, fromPlace, toPlace, queryParams);
        Persistence.tripRequests.create(tripRequest);
        return tripRequest;
    }

    /**
     * Create trip summary from static plan response file and store in database.
     */
    public static TripSummary createTripSummary() {
        TripSummary tripSummary = new TripSummary(PLAN_RESPONSE.plan, PLAN_RESPONSE.error, TRIP_REQUEST_ID);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError() {
        TripSummary tripSummary = new TripSummary(null, PLAN_ERROR_RESPONSE.error, TRIP_REQUEST_ID);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create multiple trip requests and store in database.
     */
    public static List<TripRequest> createTripRequests(int amount, String userId) {
        List<TripRequest> tripRequests = new ArrayList<>();
        int i = 0;
        while (i < amount) {
            tripRequests.add(createTripRequest(userId));
            i++;
        }
        return tripRequests;
    }

    /**
     * Delete multiple trip requests from database.
     */
    public static void deleteTripRequests(List<TripRequest> tripRequests) {
        for (TripRequest tripRequest: tripRequests) {
            Persistence.tripRequests.removeById(tripRequest.id);
        }
    }

    /**
     * Get plan responses from file for creating trip summaries.
     */
    public static void stagePlanResponses() {
        final String filePath = "src/test/resources/org/opentripplanner/middleware/";
        PLAN_RESPONSE = FileUtils.getFileContentsAsJSON(filePath + "planResponse.json", Response.class);
        PLAN_ERROR_RESPONSE = FileUtils.getFileContentsAsJSON(filePath + "planErrorResponse.json", Response.class);
    }
}
