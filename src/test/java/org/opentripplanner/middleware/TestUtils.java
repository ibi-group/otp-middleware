package org.opentripplanner.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static spark.Service.ignite;


public class TestUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TestUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Base URL for application running during testing.
     */
    private static final String BASE_URL = "http://localhost:4567/";

    /**
     * Password used to create and validate temporary Auth0 users
     */
    static final String TEMP_AUTH0_USER_PASSWORD = UUID.randomUUID().toString();

    /**
     * x-api-key used when auth is disabled.
     */
    static final String TEMP_X_API_KEY = UUID.randomUUID().toString();

    /**
     * Whether the end-to-end environment variable is enabled.
     */
    public static final boolean isEndToEnd = getBooleanEnvVar("RUN_E2E");

    public static final String TEST_RESOURCE_PATH = "src/test/resources/org/opentripplanner/middleware/";

    /**
     * Prevents the mock OTP server being initialized more than once
     */
    private static boolean mockOtpServerSetUpIsDone = false;

    /**
     * A list of mock responses for the mock OTP server to return whenever a request is made to the mock OTP server.
     * These requests are returned in the order that they are entered here and the mockResponseIndex is incremented each
     * time an OTP request is made.
     */
    private static List<OtpResponse> mockResponses = Collections.EMPTY_LIST;
    private static int mockResponseIndex = -1;

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    public static <T> T getResourceFileContentsAsJSON(String resourcePathName, Class<T> clazz) throws IOException {
        return FileUtils.getFileContentsAsJSON(
            TEST_RESOURCE_PATH + resourcePathName,
            clazz
        );
    }

    /**
     * Construct http header values based on user type and status of DISABLE_AUTH config parameter. If authorization is
     * disabled, use Auth0 user ID to authenticate else attempt to get a valid 0auth token from Auth0 and use this.
     */
    private static HashMap<String, String> getMockHeaders(AbstractUser requestingUser) {
        HashMap<String, String> headers = new HashMap<>();
        // If auth is disabled, simply place the Auth0 user ID in the authorization header, which will be extracted from
        // the request when received.
        if (isAuthDisabled()) {
            headers.put("Authorization", requestingUser.auth0UserId);
            headers.put("x-api-key", TEMP_X_API_KEY);
            return headers;
        }

        // If requester is an API user, add API key value as x-api-key header to simulate request over API Gateway.
        if (requestingUser instanceof ApiUser) {
            ApiUser apiUser = (ApiUser) requestingUser;
            if (!apiUser.apiKeys.isEmpty()) {
                headers.put("x-api-key", apiUser.apiKeys.get(0).value);
            }
        }

        // If requester is an Otp user which was created by an Api user, return empty header because an Otp user created
        // by an Api user can not directly access the middleware.
        if (requestingUser instanceof OtpUser) {
            OtpUser otpUserFromDB = Persistence.otpUsers.getById(requestingUser.id);
            if (otpUserFromDB != null && otpUserFromDB.applicationId != null) {
                return headers;
            }
        }

        // Otherwise, get a valid oauth token for the user
        headers.put("Authorization", "Bearer " + Auth0Users.getAuth0AccessToken(requestingUser.email, TEMP_AUTH0_USER_PASSWORD));
        return headers;
    }


    /**
     * Send request to provided URL.
     */
    static HttpResponse<String> makeRequest(String path, String body, HashMap<String, String> headers,
                                            HttpUtils.REQUEST_METHOD requestMethod) {
        return HttpUtils.httpRequestRawResponse(
            URI.create(BASE_URL + path),
            1000,
            requestMethod,
            headers,
            body
        );
    }

    /**
     * Send 'get' request to provided URL.
     */
    static HttpResponse<String> makeGetRequest(String path, HashMap<String, String> headers) {
        return HttpUtils.httpRequestRawResponse(
            URI.create(BASE_URL + path),
            1000,
            HttpUtils.REQUEST_METHOD.GET,
            headers,
            ""
        );
    }

    /**
     * Send 'delete' request to provided URL.
     */
    static HttpResponse<String> makeDeleteRequest(String path, HashMap<String, String> headers) {
        return HttpUtils.httpRequestRawResponse(
            URI.create(BASE_URL + path),
            1000,
            HttpUtils.REQUEST_METHOD.DELETE,
            headers,
            ""
        );
    }

    /**
     * Construct http headers according to caller request and then make an authenticated call by placing the Auth0 user
     * id in the headers so that {@link RequestingUser} can check the database for a matching user.
     */
    static HttpResponse<String> mockAuthenticatedRequest(String path, String body, AbstractUser requestingUser,
                                                         HttpUtils.REQUEST_METHOD requestMethod) {
        return makeRequest(path, body, getMockHeaders(requestingUser), requestMethod);
    }

    /**
     * Construct http headers according to caller request and then make an authenticated 'get' call.
     */
    public static HttpResponse<String> mockAuthenticatedGet(String path, AbstractUser requestingUser) {
        return makeGetRequest(path, getMockHeaders(requestingUser));
    }

    /**
     * Construct http headers according to caller request and then make an authenticated call by placing the Auth0 user
     * id in the headers so that {@link RequestingUser} can check the database for a matching user.
     */
    static HttpResponse<String> mockAuthenticatedDelete(String path, AbstractUser requestingUser) {
        return makeDeleteRequest(path, getMockHeaders(requestingUser));
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
        http.get("/otp" + OTP_PLAN_ENDPOINT, TestUtils::mockOtpPlanResponse);
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
        otpDispatcherResponse.responseBody = FileUtils.getFileContents(TEST_RESOURCE_PATH + "persistence/planResponse.json");
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
        mockResponses = Collections.EMPTY_LIST;
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
}
