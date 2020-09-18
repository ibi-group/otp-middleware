package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;

import java.io.IOException;
import java.util.HashMap;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;

/**
 * Tests for checking the existence of trips from query strings.
 */
public class ItineraryExistenceCheckerTest {
    /**
     * Map query strings to mock OTP responses.
     * We use {@link HashMap#get} function to mock {@link OtpDispatcher#sendOtpPlanRequest}
     * (both have the same signature).
     */
    private static HashMap<String, OtpDispatcherResponse> queryToResponse;

    /** Mock (significantly abbreviated) OTP response when an itinerary is not found. */
    private final static String MOCK_RESPONSE_WITH_ERROR = "{\"requestParameters\":{},\"error\":{\"id\":404,\"msg\":\"No trip found...\",\"message\":\"PATH_NOT_FOUND\",\"noPath\":true},\"debugOutput\":{}}";

    @BeforeAll
    public static void setUp() throws IOException {
        // Mock OTP responses for when itineraries exist for a query.
        String mockPlanResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        queryToResponse = new HashMap<>();

        // Queries for which an itinerary exists.
        queryToResponse.put("exist1", new OtpDispatcherResponse(mockPlanResponse));
        queryToResponse.put("exist2", new OtpDispatcherResponse(mockPlanResponse));
        queryToResponse.put("exist3", new OtpDispatcherResponse(mockPlanResponse));

        // Query for which an itinerary is not found.
        queryToResponse.put("not found", new OtpDispatcherResponse(MOCK_RESPONSE_WITH_ERROR));
    }

    @Test
    public void testAllTripsExist() {
        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker(queryToResponse::get);
        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "exist2");
        labeledQueries.put("label3", "exist3");

        ItineraryExistenceChecker.Result result = tripChecker.checkAll(labeledQueries);
        Assertions.assertTrue(result.allItinerariesExist);

        for (String label : labeledQueries.keySet()) {
            Assertions.assertNotNull(result.labeledResponses.get(label));
        }
    }

    @Test
    public void testAtLeastOneTripDoesNotExist() {
        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker(queryToResponse::get);
        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "not found");
        labeledQueries.put("label3", "exist3");

        ItineraryExistenceChecker.Result result = tripChecker.checkAll(labeledQueries);
        Assertions.assertFalse(result.allItinerariesExist);
    }

    @Test
    public void testThrowIfNullArgument() {
        Assertions.assertThrows(NullPointerException.class, () ->  new ItineraryExistenceChecker(null));
    }
}
