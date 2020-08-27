package org.opentripplanner.middleware;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;

import static org.opentripplanner.middleware.auth.Auth0Users.get0AuthToken;

public class TestUtils {

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
            String token = get0AuthToken(requestingUser.email, TEMP_AUTH0_USER_PASSWORD);
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
}
