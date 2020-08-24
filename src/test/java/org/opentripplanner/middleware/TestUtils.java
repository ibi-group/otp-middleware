package org.opentripplanner.middleware;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;

import static org.opentripplanner.middleware.auth.Auth0Users.AUTH0_DOMAIN;
import static org.opentripplanner.middleware.auth.Auth0Users.getOAuthToken;
import static org.opentripplanner.middleware.utils.HttpUtils.httpRequest;

public class TestUtils {

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    /**
     * Send request to provided URL placing the Auth0 user id in the headers so that {@link Auth0UserProfile} can check
     * the database for a matching user. Returns the response.
     */
    public static HttpResponse<String> mockAuthenticatedRequest(String path, HttpUtils.REQUEST_METHOD requestMethod, AbstractUser requestingUser) {
        HashMap<String, String> headers = getMockHeaders(requestingUser);
        headers.put("Authorization", requestingUser.auth0UserId);
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
     * FIXME: Need to do this:
     *  - https://auth0.com/docs/dev-lifecycle/work-with-auth0-locally
     *  - https://auth0.com/docs/flows/call-your-api-using-resource-owner-password-flow
     * @param requestingUser
     * @return
     */
    private static HashMap<String, String> getMockHeaders(AbstractUser requestingUser) throws UnsupportedEncodingException {
        HashMap<String, String> headers = new HashMap<>();
        // If auth is disabled, simply place the Auth0 user ID in the authorization header, which will be extracted from
        // the request when received.
        if ("true".equals(OtpMiddlewareMain.getConfigPropertyAsText("DISABLE_AUTH"))) {
            headers.put("Authorization", requestingUser.auth0UserId);
        } else {
            // Otherwise, get a valid oauth token for the user
            String password = System.getenv("password");
            String token = getOAuthToken(requestingUser.email, password);
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
