package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.mockAuthenticatedRequest;

public class OtpUserControllerTest {
    private static final String INITIAL_PHONE_NUMBER = "+15555550222"; // Fake US 555 number.
    private static OtpUser otpUser;

    /**
     * End-to End must be enabled and Auth must be disabled for tests to run.
     */
    private static boolean testsShouldRun() {
        return TestUtils.isEndToEndAndAuthIsDisabled();
    }

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        // Load config before checking if tests should run.
        OtpMiddlewareTest.setUp();
        assumeTrue(testsShouldRun());

        // Create a persisted OTP user.
        otpUser = new OtpUser();
        otpUser.email = String.format("test-%s@example.com", UUID.randomUUID().toString());
        otpUser.hasConsentedToTerms = true;
        otpUser.phoneNumber = INITIAL_PHONE_NUMBER;
        otpUser.isPhoneNumberVerified = true;
        Persistence.otpUsers.create(otpUser);
    }

    /**
     * Delete the users if they were not already deleted during the test script.
     */
    @AfterAll
    public static void tearDown() {
        assumeTrue(testsShouldRun());
        otpUser = Persistence.otpUsers.getById(otpUser.id);
        if (otpUser != null) otpUser.delete();
    }

    /**
     * Check that if for some reason the verification SMS is not sent (invalid format or other error from Twilio),
     * the request is 400-bad request/500-error, and that the user phone number is unchanged.
     */
    @ParameterizedTest
    @MethodSource("createBadPhoneNumbers")
    public void invalidNumbersShouldProduceBadRequest(Map.Entry<String, Integer> testCase) {
        assumeTrue(testsShouldRun());
        String badNumber = testCase.getKey();
        int statusCode = testCase.getValue();

        // 1. Request verification SMS.
        // The invalid number should fail the call.
        HttpResponse<String> response = mockAuthenticatedRequest(
            String.format("api/secure/user/%s/verify_sms/%s",
                otpUser.id,
                badNumber
            ),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(statusCode, response.statusCode());

        // 2. Fetch the newly-created user.
        // The phone number should not be updated.
        HttpResponse<String> otpUserWithPhoneRequest = mockAuthenticatedRequest(
            String.format("api/secure/user/%s", otpUser.id),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(HttpStatus.OK_200, otpUserWithPhoneRequest.statusCode());

        OtpUser otpUserWithPhone = JsonUtils.getPOJOFromJSON(otpUserWithPhoneRequest.body(), OtpUser.class);
        assertEquals(INITIAL_PHONE_NUMBER, otpUserWithPhone.phoneNumber);
        assertTrue(otpUserWithPhone.isPhoneNumberVerified);
    }

    private static Set<Map.Entry<String, Integer>> createBadPhoneNumbers() {
        HashMap<String, Integer> cases = new HashMap<>();
        cases.put("5555555", HttpStatus.BAD_REQUEST_400);
        cases.put("+15555550001", HttpStatus.INTERNAL_SERVER_ERROR_500);

        return cases.entrySet();
    }

    /**
     * Tests that phone numbers meet the E.164 format (e.g. +1555555).
     */
    @ParameterizedTest
    @MethodSource("createPhoneNumberTestCases")
    public void isPhoneNumberValidE164(Map.Entry<String, Boolean> testCase) {
        assumeTrue(testsShouldRun());
        String number = testCase.getKey();
        boolean isValid = testCase.getValue();

        assertEquals(isValid, OtpUserController.isPhoneNumberValidE164(number));
    }

    private static Set<Map.Entry<String, Boolean>> createPhoneNumberTestCases() {
        HashMap<String, Boolean> cases = new HashMap<>();
        cases.put("+15555550123", true);
        cases.put("+1 5555550123", false); // no spaces allowed.
        cases.put("(555) 555,0123", false);
        cases.put("555555", false);

        return cases.entrySet();
    }
}
