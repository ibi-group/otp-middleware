package org.opentripplanner.middleware;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.auth.Auth0Users.getAuth0Token;
import static spark.Service.ignite;


public class TestUtils {

    /**
     * Prevents the mock OTP server being initialized more than once
     */
    private static boolean mockOtpServerSetUpIsDone = false;

    /**
     * Mock plan endpoint used by the mock OTP server.
     */
    static final String MOCK_OTP_PLAN_ENDPOINT = "otp/plan";

    /**
     * Password used to create and validate temporary Auth0 users
     */
    static final String TEMP_AUTH0_USER_PASSWORD = "t3mp-pa$$w0rd";

    /**
     * Send request to provided URL placing the Auth0 user id in the headers so that {@link Auth0UserProfile} can check
     * the database for a matching user. Returns the response.
     */
    public static HttpResponse<String> mockAuthenticatedRequest(String path, AbstractUser requestingUser, HttpUtils.REQUEST_METHOD requestMethod) {
        HashMap<String, String> headers = getMockHeaders(requestingUser);
        // If requester is an API user, add API key value as x-api-key header to simulate request over API Gateway.
        if (requestingUser instanceof ApiUser) {
            ApiUser apiUser = (ApiUser) requestingUser;
            if (!apiUser.apiKeys.isEmpty()) {
                headers.put("x-api-key", apiUser.apiKeys.get(0).value);
            }
        }

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
        if ("true".equals(OtpMiddlewareMain.getConfigPropertyAsText("DISABLE_AUTH"))) {
            headers.put("Authorization", requestingUser.auth0UserId);
        } else {
            // Otherwise, get a valid oauth token for the user
            String token = getAuth0Token(requestingUser.email, TEMP_AUTH0_USER_PASSWORD);
            headers.put("Authorization", "Bearer " + token);
        }
        // If requester is an API user, add API key value as x-api-key header to simulate request over API Gateway.
        if (requestingUser instanceof ApiUser) {
            ApiUser apiUser = (ApiUser) requestingUser;
            if (!apiUser.apiKeys.isEmpty()) {
                headers.put("x-api-key", apiUser.apiKeys.get(0).value);
            }
        }
        return headers;
    }

    /**
     * Send request to provided URL placing the Auth0 user id in the headers so that {@link Auth0UserProfile} can check
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
     * Configure a mock OTP server for providing mock OTP responses.
     */
    static void mockOtpServer() {
        if (mockOtpServerSetUpIsDone) {
            return;
        }
        Service http = ignite().port(8080);
        http.get(MOCK_OTP_PLAN_ENDPOINT, TestUtils::mockOtpPlanResponse);
    }

    /**
     * Mock an OTP server plan response by provide a static response from file.
     */
    private static String mockOtpPlanResponse(Request request, Response response) {
        final String filePath = "src/test/resources/org/opentripplanner/middleware/";
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.statusCode = HttpStatus.OK_200;
        otpDispatcherResponse.responseBody = FileUtils.getFileContents(filePath + "planResponse.json");

        response.type(APPLICATION_JSON);
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
    }
}
