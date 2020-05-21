package org.opentripplanner.middleware.persistence;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.core.api.model.TripPlan;
import org.opentripplanner.middleware.otp.core.api.resource.Response;

import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.OtpDispatcherHelper.getPlanErrorFromOtp;
import static org.opentripplanner.middleware.OtpDispatcherHelper.getPlanFromOtp;

public class PersistenceUtil {

    private static final String batchId = "783726";
    private static final String tripRequestId = "59382";

    /**
     * Utility to create user and store in database.
     */
    public static User createUser(String email) {
        User user = new User();
        user.email = email;
        Persistence.users.create(user);
        return user;
    }

    public static TripRequest createTripRequest(String userId) {
        String fromPlace = "28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034";
        String toPlace = "28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759";
        String queryParams = "arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";
        TripRequest tripRequest = new TripRequest(userId, batchId, fromPlace, toPlace, queryParams);
        Persistence.tripRequest.create(tripRequest);
        return tripRequest;
    }

    public static TripSummary createTripSummary() {
        OtpDispatcherResponse response = getPlanFromOtp();
        Response otpResponse = response.getResponse();
        TripSummary tripSummary = new TripSummary(otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary, tripRequestId);
        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
    }

    public static TripSummary createTripSummaryWithError() {
        OtpDispatcherResponse response = getPlanErrorFromOtp();
        Response otpResponse = response.getResponse();
        TripPlan tripPlan = otpResponse.getPlan();
        TripSummary tripSummary;
        if (tripPlan != null) {
            tripSummary = new TripSummary(otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary, tripRequestId);
        } else {
            tripSummary = new TripSummary(otpResponse.getError(), tripRequestId);
        }
        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
    }

    public static List<TripRequest> createTripRequests(int amount, String userId) {
        List<TripRequest> tripRequests = new ArrayList<>();
        int i = 0;
        while (i < amount) {
            tripRequests.add(createTripRequest(userId));
            i++;
        }
        return tripRequests;
    }

    public static void deleteTripRequests(List<TripRequest> tripRequests) {
        for (TripRequest tripRequest: tripRequests) {
            Persistence.tripRequest.removeById(tripRequest.id);
        }
    }

}
