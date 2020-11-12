package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.TestUtils.isEndToEnd;
import static org.opentripplanner.middleware.auth.Auth0Connection.*;
import static org.opentripplanner.middleware.controllers.api.ApiUserController.API_USER_PATH;
import static org.opentripplanner.middleware.controllers.api.OtpUserController.OTP_USER_PATH;
import static org.opentripplanner.middleware.utils.HttpUtils.REQUEST_METHOD.GET;
import static org.opentripplanner.middleware.utils.HttpUtils.REQUEST_METHOD.POST;

/**
 * Tests for select methods from {@link Auth0Connection}
 */
public class Auth0ConnectionTest {

    private static OtpUser otpUser;
    private static User auth0User;

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        assumeTrue(isEndToEnd);
        OtpMiddlewareTest.setUp();
        setAuthDisabled(false);

        // The user to be created (this instance is not persisted).
        otpUser = new OtpUser();
        otpUser.email = String.format("test-%s@example.com", UUID.randomUUID().toString());

        // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
        // from being able to use TEMP_AUTH0_USER_PASSWORD.
        auth0User = Auth0Users.createAuth0UserForEmail(otpUser.email, TEMP_AUTH0_USER_PASSWORD);
        otpUser.auth0UserId = auth0User.getId();
    }
    
    @AfterAll
    public static void tearDown() throws Auth0Exception {
        assumeTrue(isEndToEnd);
        restoreDefaultAuthDisabled();

        // Delete the auth0 user created above.
        Auth0Users.deleteAuth0User(auth0User.getId());
    }

    @ParameterizedTest
    @MethodSource("createIsCreatingSelfTestCases")
    public void canCheckIsCreatingSelf(Auth0ConnectionTestCase testCase) throws Auth0Exception {
        // create an OtpUser/ApiUser authenticating as self
        // (An OTP user is passed solely to generate request headers, so that should work for API users too.)
        HttpResponse<String> createUserResponse = TestUtils.mockAuthenticatedRequest(testCase.uri,
            String.format("{\"auth0UserId\": \"%s\",  \"email\": \"%s\"}", otpUser.auth0UserId, otpUser.email),
            otpUser,
            testCase.method
        );

        assertEquals(testCase.result, createUserResponse.statusCode(), testCase.message);

        // Delete the created user.
        if (testCase.result == OK_200) {
            boolean creatingOtpUser = testCase.uri.endsWith(OTP_USER_PATH);
            boolean creatingApiUser = testCase.uri.endsWith(API_USER_PATH);
            if (creatingOtpUser) {
                OtpUser createdUser = JsonUtils.getPOJOFromJSON(createUserResponse.body(), OtpUser.class);
                createdUser.delete(false);
            } else if (creatingApiUser) {
                ApiUser createdUser = JsonUtils.getPOJOFromJSON(createUserResponse.body(), ApiUser.class);
                createdUser.delete(false);
            }
        }
    }

    private static Stream<Auth0ConnectionTestCase> createIsCreatingSelfTestCases() {
        return Stream.of(
            new Auth0ConnectionTestCase(POST, "api/secure/user", OK_200, "POST OtpUser"),
            new Auth0ConnectionTestCase(POST, "api/secure/application", OK_200, "POST ApiUser"),
            new Auth0ConnectionTestCase(GET, "api/secure/user", NOT_FOUND_404, "GET OtpUser"),
            new Auth0ConnectionTestCase(GET, "api/secure/application", NOT_FOUND_404, "GET ApiUser"),
            new Auth0ConnectionTestCase(POST, "api/secure/application/1234", NOT_FOUND_404, "POST ApiUser sub url"),
            new Auth0ConnectionTestCase(POST, "other/endpoint", NOT_FOUND_404, "POST invalid url")
        );
    }

    /**
     * Holds the data for the test above.
     */
    private static class Auth0ConnectionTestCase {
        public final String uri;
        public final HttpUtils.REQUEST_METHOD method;
        public final int result;
        public final String message;

        public Auth0ConnectionTestCase(HttpUtils.REQUEST_METHOD method, String uri, int result, String message) {
            this.uri = uri;
            this.method = method;
            this.result = result;
            this.message = message;
        }
    }
}
