package org.opentripplanner.middleware.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpVersion;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.ItineraryUtilsTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;
import static spark.Service.ignite;

public class OtpTestUtils {
    private static final Logger LOG = LoggerFactory.getLogger(OtpTestUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

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
     * The response contains an itinerary with a request with the following request parameters:
     * - arriveBy: false
     * - date: 2020-06-09 (a Tuesday)
     * - desired start time: 08:35
     * - itinerary start time: 08:40:10
     * - fromPlace: 1709 NW Irving St, Portland 97209::45.527817334203,-122.68865964147231
     * - toPlace: Uncharted Realities, SW 3rd Ave, Downtown - Portland 97204::45.51639151281627,-122.67681483620306
     * - first itinerary end time: 8:58:44am
     */
    public static final OtpDispatcherResponse OTP_DISPATCHER_PLAN_RESPONSE =
        initializeMockPlanResponse("otp/response/planResponse.json");

    /** Contains an OTP response with no itinerary found. */
    public static final OtpDispatcherResponse OTP_DISPATCHER_PLAN_ERROR_RESPONSE =
        initializeMockPlanResponse("otp/response/planErrorResponse.json");


    /** OTP2 plan mock response. */
    public static final OtpDispatcherResponse OTP2_DISPATCHER_PLAN_RESPONSE =
        initializeMockPlanResponse("otp/response/planResponse-otp2.json");

    /**
     * Prevents the mock OTP server from being initialized more than once
     */
    private static boolean mockOtpServerSetUpIsDone = false;

    /**
     * A list of mock responses for the mock OTP server to return whenever a request is made to the mock OTP server.
     * These requests are returned in the order that they are entered here and the mockResponseIndex is incremented each
     * time an OTP request is made.
     */
    private static List<OtpResponse> mockResponses = Collections.emptyList();
    private static int mockResponseIndex = -1;

    public static OtpDispatcherResponse initializeMockPlanResponse(String path) {
        // Contains an OTP response with an itinerary found.
        // (We are reusing an existing response. The exact contents of the response does not matter
        // for the purposes of this class.)
        String mockPlanResponse = null;
        try {
            mockPlanResponse = CommonTestUtils.getTestResourceAsString(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new OtpDispatcherResponse(mockPlanResponse, DEFAULT_PLAN_URI);
    }

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
        return OTP_DISPATCHER_PLAN_RESPONSE.responseBody;
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
            OtpVersion.OTP1,
            "28.45119,-81.36818",
            "28.54834,-81.37745",
            "08:35"
        );
    }

    public static List<OtpResponse> createMockOtpResponsesForTripExistence() throws Exception {
        // Set up monitored days and mock responses for itinerary existence check, ordered by day.
        LocalDate today = DateTimeUtils.nowAsLocalDate();
        List<String> monitoredTripDates = new ArrayList<>();
        for (int i = 0; i < ItineraryUtils.ITINERARY_CHECK_WINDOW; i++) {
            monitoredTripDates.add(DateTimeUtils.DEFAULT_DATE_FORMATTER.format(today.plusDays(i)));
        }
        return ItineraryUtilsTest.getMockDatedOtpResponses(monitoredTripDates);
    }

    /**
     * Offsets all times in the given itinerary relative to the given base time. The base time is assumed to be the new
     * start time for the itinerary. Whatever the offset from the initial itinerary's start time and the new start time
     * will be the offset that is applied to all other times in the itinerary.
     */
    public static void updateBaseItineraryTime(Itinerary mockItinerary, ZonedDateTime baseZonedDateTime) {
        mockItinerary.offsetTimes(
            baseZonedDateTime.toInstant().toEpochMilli() - mockItinerary.startTime.getTime()
        );
    }

    public static Itinerary createDefaultItinerary() throws Exception {
        return OTP_DISPATCHER_PLAN_RESPONSE.clone().getResponse().plan.itineraries.get(0);
    }

    public static JourneyState createDefaultJourneyState() throws Exception {
        JourneyState journeyState = new JourneyState();
        Itinerary defaultItinerary = createDefaultItinerary();
        journeyState.scheduledArrivalTimeEpochMillis = defaultItinerary.endTime.getTime();
        journeyState.scheduledDepartureTimeEpochMillis = defaultItinerary.startTime.getTime();
        journeyState.baselineArrivalTimeEpochMillis = defaultItinerary.endTime.getTime();
        journeyState.baselineDepartureTimeEpochMillis = defaultItinerary.startTime.getTime();
        return journeyState;
    }
}
