package org.opentripplanner.middleware;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.AbstractUser;
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
 * Tests for select methods from {@link Auth0Connection}.
 */
public class Auth0ConnectionTest {

    /**
     * This dummy AbstractUser only holds an email and auth0 id and is passed solely to generate request headers.
     * It is indifferently initialized as an OtpUser. This user is not persisted.
     */
    private static AbstractUser dummyRequestingUser = new OtpUser();
    /**
     * A real auth0 user, created to provide an authorization token.
     */
    private static User auth0User;

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        assumeTrue(isEndToEnd);
        OtpMiddlewareTest.setUp();
        setAuthDisabled(false);

        dummyRequestingUser = new OtpUser();
        dummyRequestingUser.email = String.format("test-%s@example.com", UUID.randomUUID().toString());

        // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
        // from being able to use TEMP_AUTH0_USER_PASSWORD.
        auth0User = Auth0Users.createAuth0UserForEmail(dummyRequestingUser.email, TEMP_AUTH0_USER_PASSWORD);
        dummyRequestingUser.auth0UserId = auth0User.getId();
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
    public void canCheckIsCreatingSelf(Auth0ConnectionTestCase testCase) {
        // Simulate a yet-to-be-saved OtpUser/ApiUser sending an authenticated request to persist itself
        // (e.g. during sign up).
        HttpResponse<String> createUserResponse = TestUtils.mockAuthenticatedRequest(testCase.uri,
            String.format("{\"auth0UserId\": \"%s\",  \"email\": \"%s\"}",
                dummyRequestingUser.auth0UserId,
                dummyRequestingUser.email
            ),
            dummyRequestingUser,
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

    @ParameterizedTest
    @MethodSource("createIsRequestingVerificationEmailTestCases")
    public void canCheckIsRequestingVerificationEmail(Auth0ConnectionTestCase testCase) {
        // Simulate a yet-to-be-saved OtpUser/ApiUser sending an authenticated request to resend a verification email
        // (e.g. during sign up).
        HttpResponse<String> sendVerificationEmailResponse = TestUtils.mockAuthenticatedGet(testCase.uri, dummyRequestingUser);
        assertEquals(testCase.result, sendVerificationEmailResponse.statusCode(), testCase.message);
    }

    private static Stream<Auth0ConnectionTestCase> createIsRequestingVerificationEmailTestCases() {
        return Stream.of(
            new Auth0ConnectionTestCase(GET, "api/secure/user/verification-email", OK_200, "OtpUser verification"),
            new Auth0ConnectionTestCase(GET, "api/secure/application/verification-email", OK_200, "ApiUser verification"),
            new Auth0ConnectionTestCase(GET, "api/secure/user", NOT_FOUND_404, "OtpUser other route"),
            new Auth0ConnectionTestCase(GET, "api/secure/application", NOT_FOUND_404, "ApiUser other route"),
            new Auth0ConnectionTestCase(POST, "api/secure/application/verification-email/1234", NOT_FOUND_404, "ApiUser sub url"),
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
