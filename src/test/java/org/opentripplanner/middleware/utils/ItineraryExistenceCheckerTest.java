package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.OtpResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcherResponseTest.DEFAULT_PLAN_URI;

/**
 * Tests for checking the existence of trips from query strings.
 */
public class ItineraryExistenceCheckerTest extends OtpMiddlewareTest {
    private static OtpDispatcherResponse otpDispatcherPlanResponse;
    private static OtpDispatcherResponse otpDispatcherPlanErrorResponse;

    @BeforeAll
    public static void setup() throws IOException {
        TestUtils.mockOtpServer();

         // Contains an OTP response with an itinerary found.
         // (We are reusing an existing response. The exact contents of the response does not matter
         // for the purposes of this class.)
        String mockPlanResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        // Contains an OTP response with no itinerary found.
        String mockErrorResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planErrorResponse.json"
        );

        otpDispatcherPlanResponse = new OtpDispatcherResponse(mockPlanResponse, DEFAULT_PLAN_URI);
        otpDispatcherPlanErrorResponse = new OtpDispatcherResponse(mockErrorResponse, DEFAULT_PLAN_URI);
    }

    @AfterEach
    public void tearDownAfterTest() {
        TestUtils.resetOtpMocks();
    }

    @Test
    public void testAllTripsExist() {
        // Set mocks to a list of responses with itineraries.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, resp, resp));

        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker();
        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "exist2");
        labeledQueries.put("label3", "exist3");

        ItineraryExistenceChecker.Result result = tripChecker.checkAll(labeledQueries, false);
        Assertions.assertTrue(result.allItinerariesExist);

        for (String label : labeledQueries.keySet()) {
            Assertions.assertNotNull(result.labeledResponses.get(label));
        }
    }

    @Test
    public void testAtLeastOneTripDoesNotExist() {
        // Set mocks to a list of responses, one without an itinerary.
        OtpResponse resp = otpDispatcherPlanResponse.getResponse();
        TestUtils.setupOtpMocks(List.of(resp, otpDispatcherPlanErrorResponse.getResponse(), resp));

        ItineraryExistenceChecker tripChecker = new ItineraryExistenceChecker();
        HashMap<String, String> labeledQueries = new HashMap<>();
        labeledQueries.put("label1", "exist1");
        labeledQueries.put("label2", "not found");
        labeledQueries.put("label3", "exist3");

        ItineraryExistenceChecker.Result result = tripChecker.checkAll(labeledQueries, false);
        Assertions.assertFalse(result.allItinerariesExist);
    }
}
