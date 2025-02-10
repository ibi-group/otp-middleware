package org.opentripplanner.middleware.auth;

import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Users.AUTH0_API_SECRET;
import static org.opentripplanner.middleware.auth.Auth0Users.AUTH0_DOMAIN;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.createAndAssignAuth0User;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

public class Auth0UsersTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(Auth0UsersTest.class);
    private static final long TOKEN_DURATION_SECONDS = 86400;
    private static ObjectMapper mapper = new ObjectMapper();
    private static OtpUser otpUser;
    private static final String AUTH0_CLIENT_ID = getConfigPropertyAsText("AUTH0_CLIENT_ID");

    @BeforeAll
    public static void setUp() throws IOException {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);
        LOG.info("Setting up Auth0UsersTest");
        // Construct simplified token POJO that just contains expiration duration in seconds (one day).
        ObjectNode fakeToken = mapper.createObjectNode();
        fakeToken.put("expires_in", TOKEN_DURATION_SECONDS);
        TokenHolder tokenHolder = mapper.treeToValue(fakeToken, TokenHolder.class);
        // Set new cached token.
        Auth0Users.setCachedToken(new TokenCache(tokenHolder));
    }

    @AfterAll
    public static void tearDown() {
        assumeTrue(IS_END_TO_END);
        restoreDefaultAuthDisabled();
        LOG.info("Clearing Auth0UsersTest fake token");
        // Set new cached token.
        Auth0Users.setCachedToken(null);
        if (otpUser != null) {
            otpUser.delete();
        }
    }

    /**
     * Checks that token is valid soon after creation and seconds until expiration does diminish.
     */
    @Test
    public void isTokenStale() throws InterruptedException {
        final int MILLIS = 2500;

        // Verify that seconds until expiration has decreased since first set.
        long secondsBefore = Auth0Users.getCachedToken().secondsUntilExpiration();
        Thread.sleep(MILLIS);
        long secondsAfter = Auth0Users.getCachedToken().secondsUntilExpiration();
        assertTrue(secondsAfter <= secondsBefore);

        // MILLIS / 1000 is an int division and the result will be rounded down.
        assertTrue(secondsAfter + (MILLIS / 1000) <= secondsBefore);

        // Verify that token is not expired soon after it set.
        assertFalse(Auth0Users.getCachedToken().isStale());
        assertTrue(secondsAfter < TOKEN_DURATION_SECONDS);
    }

    @Test
    void hasRevokedRefreshToken() {
        // Set new cached token.
        Auth0Users.setCachedToken(null);

        String userEmail = ApiTestUtils.generateEmailAddress("auth0-test-user");
        otpUser = PersistenceTestUtils.createUser(userEmail);
        try {
            createAndAssignAuth0User(otpUser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

//        try {
//            // URL for token refresh
//            String tokenUrl = AUTH0_DOMAIN + "/oauth/token";
//            String refreshToken = "YOUR_REFRESH_TOKEN";
//
//            // Create request body in the format expected by Auth0
//            Map<String, String> body = new HashMap<>();
//            body.put("grant_type", "refresh_token");
//            body.put("client_id", AUTH0_CLIENT_ID);
//            body.put("client_secret", AUTH0_API_SECRET);
//            body.put("refresh_token", refreshToken);
//
//            // Convert body to JSON format
//            Gson gson = new Gson();
//            String jsonBody = gson.toJson(body);
//
//            // Prepare the HttpRequest
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(java.net.URI.create(tokenUrl))
//                    .header("Content-Type", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
//                    .build();
//
//            // Create HttpClient and send request
//            HttpClient client = HttpClient.newHttpClient();
//            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//            // Handle response
//            if (response.statusCode() == 200) {
//                // Success: Print the response body (which will contain the new access and refresh tokens)
//                System.out.println("Response: " + response.body());
//            } else {
//                // Failure: Print error details
//                System.out.println("Error: " + response.statusCode() + " " + response.body());
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        try {
            String tokenEndpoint = "https://" + AUTH0_DOMAIN + "/oauth/token";

            // Create the HTTP connection
            URL url = new URL(tokenEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Prepare the request payload
            String payload = String.format(
                    "{"
                            + "\"grant_type\": \"password\","
                            + "\"client_id\": \"%s\","
                            + "\"client_secret\": \"%s\","
                            + "\"username\": \"%s\","
                            + "\"password\": \"%s\","
                            + "\"scope\": \"offline_access openid profile email\""
                            + "}",
                    AUTH0_CLIENT_ID, AUTH0_API_SECRET, otpUser.email, TEMP_AUTH0_USER_PASSWORD
            );

            // Send the request payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check the response
            int statusCode = connection.getResponseCode();
            if (statusCode == 200) {
                // Parse the response (e.g., using a JSON library)
                System.out.println("Authentication successful! Tokens received.");
            } else {
                System.err.println("Failed to authenticate. HTTP Status: " + statusCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
