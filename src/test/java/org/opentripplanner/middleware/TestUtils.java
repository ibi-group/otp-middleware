package org.opentripplanner.middleware;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;

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
    public static HttpResponse<String> mockAuthenticatedRequest(String path, HttpUtils.REQUEST_METHOD requestMethod, String auth0UserId) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", auth0UserId);

        return HttpUtils.httpRequestRawResponse(
            URI.create("http://localhost:4567/" + path),
            1000,
            requestMethod,
            headers,
            ""
        );
    }

}
