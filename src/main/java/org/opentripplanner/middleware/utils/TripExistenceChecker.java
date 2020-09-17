package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
 * This utility class checks for existence of trips for given queries.
 */
public class TripExistenceChecker {
    private Function<String, OtpDispatcherResponse> otpDispatherFunction;

    /**
     * Class constructor.
     * @param otpDispatcherFunction a function that takes String as input and returns {@link OtpDispatcherResponse},
     *                              such as {@link OtpDispatcher#sendOtpPlanRequest}.
     */
    public TripExistenceChecker(Function<String, OtpDispatcherResponse> otpDispatcherFunction) {
        if (otpDispatcherFunction == null) throw new NullPointerException();
        this.otpDispatherFunction = otpDispatcherFunction;
    }

    /**
     * Checks for the given queries, one by one, that an itinerary exists
     * (that the OTP response contains a "plan" entry).
     * @param queries the list of queries to check.
     * @return false if at least one query does not result in an itinerary (or an error occurs), true otherwise.
     */
    public boolean checkExistenceOfAllTrips(List<String> queries) {
        // TODO: Consider multi-threading
        for (String query : queries) {
            OtpDispatcherResponse response = otpDispatherFunction.apply(query);
            if (!response.containsAPlan()) return false;
        }
        return true;
    }
}
