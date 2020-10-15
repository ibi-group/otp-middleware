package org.opentripplanner.middleware;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;
import static spark.Service.ignite;


public class TestUtils {
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
     * Password used to create and validate temporary Auth0 users
     */
    static final String TEMP_AUTH0_USER_PASSWORD = UUID.randomUUID().toString();

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
     * Send request to provided URL placing the Auth0 user id in the headers so that {@link RequestingUser} can check
     * the database for a matching user. Returns the response.
     */
    public static HttpResponse<String> mockAuthenticatedRequest(String path, AbstractUser requestingUser, HttpUtils.REQUEST_METHOD requestMethod) {
        HashMap<String, String> headers = getMockHeaders(requestingUser);

        return HttpUtils.httpRequestRawResponse(
            URI.create("http://localhost:4567/" + path),
            1000,
            requestMethod,
            headers,
            ""
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
            headers.put("x-api-key", requestingUser.apiKey);
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
        headers.put("Authorization", "Bearer " + Auth0Users.getAuth0Token(requestingUser.email, TEMP_AUTH0_USER_PASSWORD));
        return headers;
    }

    /**
     * Send request to provided URL placing the Auth0 user id in the headers so that {@link RequestingUser} can check
     * the database for a matching user. Returns the response.
     */
    public static HttpResponse<String> mockAuthenticatedPost(String path, AbstractUser requestingUser, String body) {
        HashMap<String, String> headers = getMockHeaders(requestingUser);

        return HttpUtils.httpRequestRawResponse(
            URI.create("http://localhost:4567/" + path),
            1000,
            HttpUtils.REQUEST_METHOD.POST,
            headers,
            body
        );
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
        mockOtpServerSetUpIsDone = true;
    }

    /**
     * Mock an OTP server plan response by provide a static response from file.
     */
    private static String mockOtpPlanResponse(Request request, Response response) throws IOException {
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.statusCode = HttpStatus.OK_200;
        otpDispatcherResponse.responseBody = FileUtils.getFileContents(TEST_RESOURCE_PATH + "persistence/planResponse.json");

        response.type(APPLICATION_JSON);
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
    }

    /**
     * Submit plan query to OTP server and return the response.
     */
    public static OtpDispatcherResponse sendSamplePlanRequest() {
        // Submit a query to the OTP server.
        // From P&R to Downtown Orlando
        return OtpDispatcher.sendOtpPlanRequest(
            "28.45119,-81.36818",
            "28.54834,-81.37745"
        );
    }
}
