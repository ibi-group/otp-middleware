package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;

public class OtpUserControllerTest {
    private static final String INITIAL_PHONE_NUMBER = "+15555550222"; // Fake US 555 number.
    private static OtpUser otpUser;

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        // Load config.
        OtpMiddlewareTest.setUp();
        // Ensure auth is disabled.
        setAuthDisabled(true);
        // Create a persisted OTP user.
        otpUser = new OtpUser();
        otpUser.email = String.format("test-%s@example.com", UUID.randomUUID().toString());
        otpUser.hasConsentedToTerms = true;
        otpUser.phoneNumber = INITIAL_PHONE_NUMBER;
        otpUser.isPhoneNumberVerified = true;
        Persistence.otpUsers.create(otpUser);
    }

    @AfterAll
    public static void tearDown() {
        // Delete the users if they were not already deleted during the test script.
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete(false);

        // Restore original isAuthDisabled state.
        restoreDefaultAuthDisabled();
    }

    /**
     * Check that if for some reason the verification SMS is not sent (invalid format or other error from Twilio),
     * the request is 400-bad request/500-error, and that the user phone number is unchanged.
     */
    @ParameterizedTest
    @MethodSource("createBadPhoneNumbers")
    public void invalidNumbersShouldProduceBadRequest(String badNumber, int statusCode) {
        // 1. Request verification SMS.
        // The invalid number should fail the call.
        HttpResponse<String> response = mockAuthenticatedGet(
            String.format("api/secure/user/%s/verify_sms/%s",
                otpUser.id,
                badNumber
            ),
            otpUser
        );
        assertEquals(statusCode, response.statusCode());

        // 2. Fetch the newly-created user.
        // The phone number should not be updated.
        HttpResponse<String> otpUserWithPhoneRequest = mockAuthenticatedGet(
            String.format("api/secure/user/%s", otpUser.id),
            otpUser
        );
        assertEquals(HttpStatus.OK_200, otpUserWithPhoneRequest.statusCode());

        OtpUser otpUserWithPhone = JsonUtils.getPOJOFromJSON(otpUserWithPhoneRequest.body(), OtpUser.class);
        assertEquals(INITIAL_PHONE_NUMBER, otpUserWithPhone.phoneNumber);
        assertTrue(otpUserWithPhone.isPhoneNumberVerified);
    }

    private static Stream<Arguments> createBadPhoneNumbers() {
        return Stream.of(
            Arguments.of("5555555", HttpStatus.BAD_REQUEST_400),
            Arguments.of("+15555550001", HttpStatus.INTERNAL_SERVER_ERROR_500)
        );
    }

    /**
     * Tests that phone numbers meet the E.164 format (e.g. +15555550123).
     */
    @ParameterizedTest
    @MethodSource("createPhoneNumberTestCases")
    public void isPhoneNumberValidE164(String number, boolean isValid) {
        assertEquals(isValid, OtpUserController.isPhoneNumberValidE164(number));
    }

    private static Stream<Arguments> createPhoneNumberTestCases() {
        return Stream.of(
            Arguments.of("+15555550123", true),
            Arguments.of("+1 5555550123", false), // no spaces allowed.
            Arguments.of("(555) 555,0123", false),
            Arguments.of("555555", false)
        );
    }
}
