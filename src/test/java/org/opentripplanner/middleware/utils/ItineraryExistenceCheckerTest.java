package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;

import java.util.HashMap;
import java.util.List;

/**
 * Tests for checking the existence of trips from query strings.
 */
public class ItineraryExistenceCheckerTest {
    /** Map query strings to mock OTP responses */
    private static HashMap<String, OtpDispatcherResponse> queryToResponse;

    /** Mock (significantly abbreviated) OTP responses for when a trip exist */
    private final static String MOCK_RESPONSE_WITH_PLAN = "{\"requestParameters\":{},\"plan\":{\"date\":1600264260000,\"from\":{},\"to\":{},\"itineraries\":[{\"duration\":797},{\"duration\":801}]},\"debugOutput\":{}}";

    /** Mock (significantly abbreviated) OTP response when an itinerary is not found. */
    private final static String MOCK_RESPONSE_WITH_ERROR = "{\"requestParameters\":{},\"error\":{\"id\":404,\"msg\":\"No trip found...\",\"message\":\"PATH_NOT_FOUND\",\"noPath\":true},\"debugOutput\":{}}";

    @BeforeAll
    public static void setUp() {
        queryToResponse = new HashMap<>();

        // Queries for which an itinerary exists.
        queryToResponse.put("exist1", new OtpDispatcherResponse(MOCK_RESPONSE_WITH_PLAN));
        queryToResponse.put("exist2", new OtpDispatcherResponse(MOCK_RESPONSE_WITH_PLAN));
        queryToResponse.put("exist3", new OtpDispatcherResponse(MOCK_RESPONSE_WITH_PLAN));

        // Query for which an itinerary is not found.
        queryToResponse.put("not found", new OtpDispatcherResponse(MOCK_RESPONSE_WITH_ERROR));
    }

    @Test
    public void testAllTripsExist() {
        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker(queryToResponse::get);
        Assertions.assertTrue(tripChecker.checkAll(List.of("exist1", "exist2", "exist3")));
    }

    @Test
    public void testAtLeastOneTripDoesNotExist() {
        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker(queryToResponse::get);
        Assertions.assertFalse(tripChecker.checkAll(List.of("exist1", "not found", "exist3")));
    }

    @Test
    public void testNullFuncArgument() {
        Assertions.assertThrows(NullPointerException.class, () ->  new ItineraryExistenceChecker(null));
    }
}
