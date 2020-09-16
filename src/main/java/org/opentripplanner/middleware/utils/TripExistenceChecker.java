package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;

import java.util.List;
import java.util.function.Function;

/**
 * This utility class checks for existence of trips for given queries.
 */
public class TripExistenceChecker {
    private Function<String, OtpDispatcherResponse> otpDispatherFunction;

    public TripExistenceChecker(Function<String, OtpDispatcherResponse> otpDispatcherFunction) {
        this.otpDispatherFunction = otpDispatcherFunction;
    }

    /**
     * Checks for the given queries, one by one, that an itinerary exists
     * (that the OTP response contains a "plan" entry).
     * @param queries the list of queries to check.
     * @return false if at least one query does not result in an itinerary (or an error occurs), true otherwise.
     */
    public boolean checkExistenceOfAllTrips(List<String> queries) {
        for (String query : queries) {
            OtpDispatcherResponse response = otpDispatherFunction.apply(query);
            JsonNode responseJson = null;
            try {
                responseJson = YamlUtils.yamlMapper.readTree(response.responseBody);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                // TODO: Bugsnag
                return false;
            }

            if (responseJson.get("plan") == null) {
                return false;
            }
        }
        return true;
    }
}
