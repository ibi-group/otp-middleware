package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpLegResponseWrapper;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class that creates mock responses to OTP leg queries.
 */
public class MockLegResponseProvider {

    /**
     * A map of mock transit legs to return.
     */
    private final Map<String, Leg> transitLegs;

    public MockLegResponseProvider(Itinerary itinerary, Function<Leg, String> getLegId) {
        this.transitLegs = itinerary.legs
            .stream()
            .filter(Leg::transitLegWithId)
            .collect(Collectors.toMap(getLegId, Function.identity()));
    }
    public MockLegResponseProvider(Itinerary itinerary) {
        this(itinerary, MockLegResponseProvider::makeUpdatedLegId);
    }

    public OtpDispatcherResponse getLegResponse(String id) {
        OtpLegResponseWrapper wrapper = new OtpLegResponseWrapper();
        wrapper.data.leg = transitLegs.get(id);

        OtpDispatcherResponse response = new OtpDispatcherResponse();
        response.responseBody = JsonUtils.toJson(wrapper);
        return response;
    }

    public static String makeUpdatedLegId(Leg leg) {
        return String.format("%s-expected", leg.id);
    }

    public static String computeLegIdForServiceDate(Leg leg, LocalDate desiredServiceDate) {
        return makeUpdatedLegId(leg);
    }
}
