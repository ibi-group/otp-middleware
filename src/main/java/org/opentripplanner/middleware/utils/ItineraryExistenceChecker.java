package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * This utility class checks for existence of itineraries for given OTP queries.
 */
public class ItineraryExistenceChecker {
    private final Function<String, OtpDispatcherResponse> otpDispatcherFunction;

    /**
     * Class constructor.
     * @param otpDispatcherFunction a function that takes String as input and returns {@link OtpDispatcherResponse},
     *                              example: {@link OtpDispatcher#sendOtpPlanRequest}.
     */
    public ItineraryExistenceChecker(Function<String, OtpDispatcherResponse> otpDispatcherFunction) {
        if (otpDispatcherFunction == null) throw new NullPointerException();
        this.otpDispatcherFunction = otpDispatcherFunction;
    }

    /**
     * Checks that, for each query provided, an itinerary exists.
     * @param labeledQueries a map with all the queries to check.
     * @return An object with a map of results and summary of itinerary existence.
     */
    public Result checkAll(Map<String, String> labeledQueries) {
        // TODO: Consider multi-threading?
        Map<String, OtpDispatcherResponse> responses = new HashMap<>();
        boolean allItinerariesExist = true;

        for (Map.Entry<String, String> entry : labeledQueries.entrySet()) {
            OtpDispatcherResponse response = otpDispatcherFunction.apply(entry.getValue());
            responses.put(entry.getKey(), response);

            Itinerary sameDayItinerary = response.findItineraryDepartingSameDay();
            if (sameDayItinerary == null) allItinerariesExist = false;
        }

        return new Result(allItinerariesExist, responses);
    }

    /**
     * Class to pass the results of the OTP itinerary checks.
     */
    public static class Result {
        public final boolean allItinerariesExist;
        public final Map<String, OtpDispatcherResponse> labeledResponses;

        private Result(boolean itinerariesExist, Map<String, OtpDispatcherResponse> labeledResponses) {
            this.allItinerariesExist = itinerariesExist;
            this.labeledResponses = labeledResponses;
        }
    }
}
