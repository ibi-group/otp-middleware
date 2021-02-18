package org.opentripplanner.middleware.testutils;

import org.eclipse.jetty.http.HttpMethod;
import com.auth0.json.auth.TokenHolder;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;

public class ApiTestUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ApiTestUtils.class);

    /**
     * Base URL for application running during testing.
     */
    public static final String BASE_URL = "http://localhost:4567/";

    /**
     * Password used to create and validate temporary Auth0 users
     */
    public static final String TEMP_AUTH0_USER_PASSWORD = UUID.randomUUID().toString();

    /**
     * x-api-key used when auth is disabled.
     */
    private static final String TEMP_X_API_KEY = UUID.randomUUID().toString();

    /**
     * Construct http header values based on user type and status of DISABLE_AUTH config parameter. If authorization is
     * disabled, use Auth0 user ID to authenticate else attempt to get a valid 0auth token from Auth0 and use this.
     */
    public static HashMap<String, String> getMockHeaders(AbstractUser requestingUser) {
        HashMap<String, String> headers = new HashMap<>();
        String scope = null;
        // If auth is disabled, set the authorization header, x-api-key and scope accordingly, each which will be
        // extracted from the request when received.
        if (isAuthDisabled()) {
            headers.put("Authorization", requestingUser.auth0UserId);
            headers.put("x-api-key", TEMP_X_API_KEY);
            if (requestingUser instanceof OtpUser) {
                headers.put("scope", OtpUser.AUTH0_SCOPE);
            } else if (requestingUser instanceof ApiUser) {
                headers.put("scope", ApiUser.AUTH0_SCOPE);
            } else if (requestingUser instanceof AdminUser) {
                headers.put("scope", AdminUser.AUTH0_SCOPE);
            }
            return headers;
        }

        // If requester is an API user, add API key value as x-api-key header to simulate request over API Gateway.
        if (requestingUser instanceof ApiUser) {
            ApiUser apiUser = (ApiUser) requestingUser;
            scope = ApiUser.AUTH0_SCOPE;
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
            scope = OtpUser.AUTH0_SCOPE;
        }

        // Otherwise, get a valid oauth token for the user
        headers.put("Authorization", "Bearer " + getTestAuth0AccessToken(requestingUser.email, scope));
        return headers;
    }

    /**
     * Attempt to get Auth0 token from Auth0 tenant for the given username (email) and scope. If the response from Auth0
     * is ok, extract the access token from the token holder response and return to caller.
     */
    private static String getTestAuth0AccessToken(String username, String scope) {
        HttpResponseValues response = Auth0Users.getAuth0TokenWithScope(username, TEMP_AUTH0_USER_PASSWORD, scope);
        if (response == null || response.status != HttpStatus.OK_200) {
            LOG.error("Cannot obtain Auth0 token for user {}. response: {} - {}",
                username,
                response.status,
                response.responseBody);
            return null;
        }
        TokenHolder token = JsonUtils.getPOJOFromJSON(response.responseBody, TokenHolder.class);
        return (token == null) ? null : token.getAccessToken();
    }


    /**
     * Send request to provided URL.
     */
    public static HttpResponseValues makeRequest(
        String path, String body, HashMap<String, String> headers, HttpMethod requestMethod
    ) {
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
    public static HttpResponseValues makeGetRequest(String path, HashMap<String, String> headers) {
        return HttpUtils.httpRequestRawResponse(
            URI.create(BASE_URL + path),
            1000,
            HttpMethod.GET,
            headers,
            ""
        );
    }

    /**
     * Send 'delete' request to provided URL.
     */
    public static HttpResponseValues makeDeleteRequest(String path, HashMap<String, String> headers) {
        return HttpUtils.httpRequestRawResponse(
            URI.create(BASE_URL + path),
            1000,
            HttpMethod.DELETE,
            headers,
            ""
        );
    }

    /**
     * Construct http headers according to caller request and then make an authenticated call by placing the Auth0 user
     * id in the headers so that {@link RequestingUser} can check the database for a matching user.
     */
    public static HttpResponseValues mockAuthenticatedRequest(
        String path, String body, AbstractUser requestingUser, HttpMethod requestMethod
    ) {
        return makeRequest(path, body, getMockHeaders(requestingUser), requestMethod);
    }

    /**
     * Construct http headers according to caller request and then make an authenticated 'get' call.
     */
    public static HttpResponseValues mockAuthenticatedGet(String path, AbstractUser requestingUser) {
        return makeGetRequest(path, getMockHeaders(requestingUser));
    }

    /**
     * Construct http headers according to caller request and then make an authenticated call by placing the Auth0 user
     * id in the headers so that {@link RequestingUser} can check the database for a matching user.
     */
    public static HttpResponseValues mockAuthenticatedDelete(String path, AbstractUser requestingUser) {
        return makeDeleteRequest(path, getMockHeaders(requestingUser));
    }

    /**
     * Generates a test email address with the specified prefix (to help trace which code created a user),
     * followed by random UUID string.
     */
    public static String generateEmailAddress(String prefix) {
        return String.format("%s-%s@example.com", prefix, UUID.randomUUID().toString());
    }

}
