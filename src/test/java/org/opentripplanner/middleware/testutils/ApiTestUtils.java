package org.opentripplanner.middleware.testutils;

import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.UUID;

import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthDisabled;

public class ApiTestUtils {
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
    static final String TEMP_X_API_KEY = UUID.randomUUID().toString();

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
        headers.put("Authorization", "Bearer " + Auth0Users
            .getAuth0AccessToken(requestingUser.email, TEMP_AUTH0_USER_PASSWORD));
        return headers;
    }

    /**
     * Send request to provided URL.
     */
    public static HttpResponse<String> makeRequest(
        String path, String body, HashMap<String, String> headers, HttpUtils.REQUEST_METHOD requestMethod
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
    public static HttpResponse<String> makeGetRequest(String path, HashMap<String, String> headers) {
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
    public static HttpResponse<String> makeDeleteRequest(String path, HashMap<String, String> headers) {
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
    public static HttpResponse<String> mockAuthenticatedRequest(
        String path, String body, AbstractUser requestingUser, HttpUtils.REQUEST_METHOD requestMethod
    ) {
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
    public static HttpResponse<String> mockAuthenticatedDelete(String path, AbstractUser requestingUser) {
        return makeDeleteRequest(path, getMockHeaders(requestingUser));
    }
}
