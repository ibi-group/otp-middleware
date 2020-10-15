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
import java.io.UnsupportedEncodingException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        otpUser.pendingPhoneNumber = null;
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
     * Test to check that a phone verification SMS request updates OtpUser.pendingPhoneNumber
     * and leaves OtpUser.phoneNumber intact.
     */
    @Test
    public void smsRequestShouldSetPendingPhoneNumberOnly() throws UnsupportedEncodingException {
        final String PHONE_NUMBER_TO_VERIFY = "+15555550321";
        final String PHONE_NUMBER_TO_VERIFY_FORMATTED = "(555) 555-0321";

        // 1. Request verification SMS.
        // Note that the result of the request for an SMS does not matter
        // (e.g. if the SMS service is down, the user's phone number should still be recorded).
        mockAuthenticatedRequest(
            String.format("api/secure/user/%s/verify_sms/%s",
                otpUser.id,
                PHONE_NUMBER_TO_VERIFY
            ),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );

        // 2. Fetch the newly-created user and check the notificationChannel, phoneNumber and isPhoneNumberVerified fields.
        // This should be the case regardless of the outcome from above.
        HttpResponse<String> otpUserWithPhoneResponse = mockAuthenticatedRequest(
            String.format("api/secure/user/%s", otpUser.id),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(HttpStatus.OK_200, otpUserWithPhoneResponse.statusCode());

        OtpUser otpUserWithPhone = JsonUtils.getPOJOFromJSON(otpUserWithPhoneResponse.body(), OtpUser.class);
        assertEquals(INITIAL_PHONE_NUMBER, otpUserWithPhone.phoneNumber);
        assertEquals(PHONE_NUMBER_TO_VERIFY, otpUserWithPhone.pendingPhoneNumber);
        assertEquals(PHONE_NUMBER_TO_VERIFY_FORMATTED, otpUserWithPhone.pendingPhoneNumberFormatted);
    }

    /**
     * Check that a request with an malformed number or an international number
     * results in a 400-bad request response.
     * The home country is set by the COUNTRY_CODE config parameter.
     */
    @ParameterizedTest
    @MethodSource("createRejectedNumbers")
    public void invalidOrForeignNumbersShouldProduceBadRequest(String number) {
        assumeTrue(NotificationUtils.COUNTRY_CODE.equals("US"));

        // 1. Request verification SMS.
        // The invalid number should fail the call.
        HttpResponse<String> response = mockAuthenticatedRequest(
            String.format("api/secure/user/%s/verify_sms/%s",
                otpUser.id,
                number
            ),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(HttpStatus.BAD_REQUEST_400, response.statusCode());

        // 2. Fetch the newly-created user.
        // pendingPhoneNumber* fields should be null.
        HttpResponse<String> otpUserWithBadPhoneRequest = mockAuthenticatedRequest(
            String.format("api/secure/user/%s", otpUser.id),
            otpUser,
            HttpUtils.REQUEST_METHOD.GET
        );
        assertEquals(HttpStatus.OK_200, otpUserWithBadPhoneRequest.statusCode());

        OtpUser otpUserWithBadPhone = JsonUtils.getPOJOFromJSON(otpUserWithBadPhoneRequest.body(), OtpUser.class);
        assertEquals(INITIAL_PHONE_NUMBER, otpUserWithBadPhone.phoneNumber);
        assertNull(otpUserWithBadPhone.pendingPhoneNumber);
        assertNull(otpUserWithBadPhone.pendingPhoneNumberFormatted);
    }

    private static List<String> createRejectedNumbers() {
        return List.of(
            // "Famous" old parisian print shop number (https://fr.wikipedia.org/wiki/Jean_Mineur)
            "+33142250001",

            // US number with invalid characters
            "+1555_&5551"
        );
    }
}
