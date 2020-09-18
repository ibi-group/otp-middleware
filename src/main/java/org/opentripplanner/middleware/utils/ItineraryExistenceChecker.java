package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.ArrayList;
import java.util.List;
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
     * @param queries the list of queries to check.
     * @return false if at least one query does not result in an itinerary (or an error occurs), true otherwise.
     */
    public Result checkAll(List<String> queries) {
        // TODO: Consider multi-threading?
        List<OtpDispatcherResponse> responses = new ArrayList<>();
        boolean allItinerariesExist = true;

        for (String query : queries) {
            OtpDispatcherResponse response = otpDispatcherFunction.apply(query);
            responses.add(response);

            Itinerary sameDayItinerary = response.findItineraryDepartingSameDay();
            if (sameDayItinerary == null) allItinerariesExist = false;
        }

        return new Result(allItinerariesExist, responses);
    }

    /**
     * Class to pass the results of the OTP itinerary checks.
     */
    public class Result {
        public final boolean allItinerariesExist;
        public final List<OtpDispatcherResponse> responses; // TODO: Map??

        private Result(boolean itinerariesExist, List<OtpDispatcherResponse> responses) {
            this.allItinerariesExist = itinerariesExist;
            this.responses = responses;
        }
    }
}
