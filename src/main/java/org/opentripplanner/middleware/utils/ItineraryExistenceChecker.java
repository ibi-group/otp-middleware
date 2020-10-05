package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * This utility class checks for existence of itineraries for given OTP queries.
 */
public class ItineraryExistenceChecker {
    /**
     * Checks that, for each query provided, an itinerary exists.
     * @param labeledQueries a map containing the queries to check, each query having a key or
     *                       label that will be used in Result.labeledResponses for easy identification.
     * @return An object with a map of results and summary of itinerary existence.
     */
    public Result checkAll(Map<String, String> labeledQueries, boolean tripIsArriveBy) {
        // TODO: Consider multi-threading?
        Map<String, OtpResponse> responses = new HashMap<>();
        boolean allItinerariesExist = true;

        for (Map.Entry<String, String> entry : labeledQueries.entrySet()) {
            OtpDispatcherResponse response = OtpDispatcher.sendOtpPlanRequest(entry.getValue());
            responses.put(entry.getKey(), response.getResponse());

            Itinerary sameDayItinerary = response.findItineraryDepartingSameDay(tripIsArriveBy);
            if (sameDayItinerary == null) allItinerariesExist = false;
        }

        return new Result(allItinerariesExist, responses);
    }

    /**
     * Class to pass the results of the OTP itinerary checks.
     */
    public static class Result {
        /** Whether all itineraries checked exist. */
        public final boolean allItinerariesExist;
        /**
         * A map with the same keys as the input from checkAll,
         * and values as OTP responses for the corresponding queries.
         */
        public final Map<String, OtpResponse> labeledResponses;

        private Result(boolean itinerariesExist, Map<String, OtpResponse> labeledResponses) {
            this.allItinerariesExist = itinerariesExist;
            this.labeledResponses = labeledResponses;
        }
    }
}
