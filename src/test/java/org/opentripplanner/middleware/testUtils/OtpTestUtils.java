package org.opentripplanner.middleware.testUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;
import static spark.Service.ignite;

public class OtpTestUtils {
    private static final Logger LOG = LoggerFactory.getLogger(OtpTestUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Prevents the mock OTP server being initialized more than once
     */
    private static boolean mockOtpServerSetUpIsDone = false;

    /**
     * A list of mock responses for the mock OTP server to return whenever a request is made to the mock OTP server.
     * These requests are returned in the order that they are entered here and the mockResponseIndex is incremented each
     * time an OTP request is made.
     */
    private static List<OtpResponse> mockResponses = Collections.emptyList();
    private static int mockResponseIndex = -1;

    private static final String responseResourceFilePath = "otp/response/";

    public static final URI DEFAULT_PLAN_URI = URI.create(
        String.format(
            "http://test.com/otp/routers/default/plan?%s",
            URLEncoder.encode(
                "date=2020-06-09&mode=WALK,BUS,TRAM,RAIL,GONDOLA&arriveBy=false&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=NaN&showIntermediateStops=true&optimize=QUICK&fromPlace=1709 NW Irving St, Portland 97209::45.527817334203,-122.68865964147231&toPlace=Uncharted Realities, SW 3rd Ave, Downtown - Portland 97204::45.51639151281627,-122.67681483620306&time=08:35&maxWalkDistance=1207",
                UTF_8
            )
        )
    );

    /**
     * Configure a mock OTP server for providing mock OTP responses. Note: this expects the config value
     * OTP_API_ROOT=http://localhost:8080/otp
     */
    public static void mockOtpServer() {
        if (mockOtpServerSetUpIsDone) {
            return;
        }
        Service http = ignite().port(8080);
        http.get("/otp" + OTP_PLAN_ENDPOINT, OtpTestUtils::mockOtpPlanResponse);
        http.get("/*", (request, response) -> {
            logMessageAndHalt(
                request,
                404,
                String.format("No API route configured for path %s.", request.uri())
            );
            return null;
        });
        mockOtpServerSetUpIsDone = true;
    }

    /**
     * Mock an OTP server plan response by serving defined mock responses or a static response from file.
     */
    private static String mockOtpPlanResponse(Request request, Response response) throws IOException {
        LOG.info("Received mock OTP request: {}?{}", request.url(), request.queryString());
        // check if mock responses have been added
        if (mockResponseIndex > -1) {
            // mock responses added. Make sure there are enough left.
            if (mockResponseIndex >= mockResponses.size()) {
                // increment once more, to make sure the actual amount of OTP mocks equaled the expected amount
                mockResponseIndex++;
                throw new RuntimeException("Unmocked request to OTP received!");
            }
            LOG.info("Returning mock response at index {}", mockResponseIndex);
            // send back response and increment response index
            String responseBody = mapper.writeValueAsString(mockResponses.get(mockResponseIndex));
            mockResponseIndex++;
            return responseBody;
        }

        // mocks not setup, simply return from a file every time
        LOG.info("Returning default mock response from file");
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.responseBody = CommonTestUtils.getResourceFileContentsAsString(
            "otp/response/planResponse.json"
        );
        return otpDispatcherResponse.responseBody;
    }

    /**
     * Provide a defined list of mock Otp Responses.
     */
    public static void setupOtpMocks(List<OtpResponse> responses) {
        mockResponses = responses;
        mockResponseIndex = 0;
        LOG.info("Added {} Otp mocks", responses.size());
    }

    /**
     * Helper method to reset the mocks and also make sure that the expected amount of requests were served
     */
    public static void resetOtpMocks() {
        if (mockResponseIndex > -1) {
            if (mockResponseIndex != mockResponses.size()) {
                throw new RuntimeException(
                    String.format(
                        "Unexpected amount of mocked OTP responses was used. Expected=%d, Actual=%d",
                        mockResponses.size(),
                        mockResponseIndex
                    )
                );
            }
            LOG.info("Reset OTP mocks!");
        }
        mockResponses = Collections.emptyList();
        mockResponseIndex = -1;
    }

    /**
     * Submit plan query to OTP server and return the response.
     */
    public static OtpDispatcherResponse sendSamplePlanRequest() {
        // Submit a query to the OTP server.
        // From P&R to Downtown Orlando
        return OtpDispatcher.sendOtpPlanRequest(
            "28.45119,-81.36818",
            "28.54834,-81.37745",
            "08:35"
        );
    }

    /**
     * Get successful plan response from file for creating trip summaries.
     */
    public static OtpResponse getPlanResponse() throws IOException {
        return CommonTestUtils.getResourceFileContentsAsJSON(
            responseResourceFilePath + "planResponse.json",
            OtpResponse.class
        );
    }

    /**
     * Get error plan response from file for creating trip summaries.
     */
    public static OtpResponse getPlanErrorResponse() throws IOException {
        return CommonTestUtils.getResourceFileContentsAsJSON(
            responseResourceFilePath + "planErrorResponse.json",
            OtpResponse.class
        );
    }

    static Itinerary createItinerary() {
        Itinerary itinerary = new Itinerary();
        itinerary.duration = 1350L;
        itinerary.elevationGained = 0.0;
        itinerary.elevationLost = 0.0;
        itinerary.endTime = new Date();
        itinerary.startTime = new Date();
        itinerary.transfers = 0;
        itinerary.transitTime = 150;
        itinerary.waitingTime = 2;
        itinerary.walkDistance = 1514.13182088778;
        itinerary.walkLimitExceeded = false;

        Leg leg = new Leg();
        leg.startTime = new Date();
        leg.endTime = new Date();
        leg.departureDelay = 10;
        leg.arrivalDelay = 10;
        leg.realTime = true;
        leg.distance = 1500.0;
        leg.pathway = true;
        leg.mode = "walk";

        Place place = new Place();
        place.lat = 28.5398938204469;
        place.lon = -81.3772773742676;
        place.name = "28.54894, -81.38971";
        place.orig = "28.54894, -81.38971";
        leg.from = place;
        leg.to = place;

        List<Leg> legs = new ArrayList<>();
        legs.add(leg);
        itinerary.legs = legs;
        return itinerary;
    }
}
