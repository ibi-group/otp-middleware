package org.opentripplanner.middleware.utils;

import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.createUser;
import static org.opentripplanner.middleware.utils.ConfigUtils.isRunningCi;
import static org.opentripplanner.middleware.utils.NotificationUtils.OTP_ADMIN_DASHBOARD_FROM_EMAIL;

/**
 * Contains tests for the various notification utilities to send SMS, email messages, and push notifications.
 * Note: these tests require the environment variables RUN_E2E=true and valid values for TEST_TO_EMAIL, TEST_TO_PHONE,
 * and TEST_TO_PUSH. Furthermore, TEST_TO_PHONE must be a verified phone number in a valid Twilio account.
 */
public class NotificationUtilsTestCI extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtilsTestCI.class);
    private static OtpUser user;

    /**
     * Note: In order to run the notification tests, these values must be provided in in system
     * environment variables, which can be defined in a run configuration in your IDE.
     */
    private static final String email = System.getenv("TEST_TO_EMAIL");
    /** Phone must be in the form "+15551234" and must be verified first in order to send notifications */
    private static final String phone = System.getenv("TEST_TO_PHONE");
    /** Push notification is conventionally a user.email value and must be known to the mobile team's push API */
    private static final String push = System.getenv("TEST_TO_PUSH");
    /**
     * Currently, since these tests require target email/SMS values, these tests should not run on CI.
     */
    private static final boolean shouldTestsRun =
            !isRunningCi && IS_END_TO_END && email != null && phone != null && push != null;

    @BeforeAll
    public static void setup() throws IOException {
        assumeTrue(shouldTestsRun);
        user = createUser(email, phone);
    }

    @AfterAll
    public static void tearDown() {
        if (user != null) Persistence.otpUsers.removeById(user.id);
    }

    @Test
    public void canSendPushNotification() {
        String ret = NotificationUtils.sendPush(
            // Conventionally user.email
            push,
            "Tough little ship!",
            "Titanic",
            "trip-id"
        );
        LOG.info("Push notification (ret={}) sent to {}", ret, push);
        Assertions.assertNotNull(ret);
    }

    @Test
    public void canSendSparkpostEmailNotification() {
        boolean success = NotificationUtils.sendEmailViaSparkpost(
            OTP_ADMIN_DASHBOARD_FROM_EMAIL,
            user.email,
            "Hi there",
            "This is the body",
            null
        );
        Assertions.assertTrue(success);
    }

    @Test
    public void canSendSmsNotification() {
        // Note: toPhone must be verified.
        String messageId = NotificationUtils.sendSMS(
            // Note: phone number is configured in setup method above.
            user.phoneNumber,
            "This is the ship that made the Kessel Run in fourteen parsecs?"
        );
        LOG.info("Notification (id={}) successfully sent to {}", messageId, user.phoneNumber);
        Assertions.assertNotNull(messageId);
    }

    /**
     * Tests whether a verification code can be sent to a phone number.
     */
    @Test
    public void canSendTwilioVerificationText() {
        Assertions.assertNull(user.smsConsentDate);
        Date beforeVerificationDate = new Date();
        Verification verification = NotificationUtils.sendVerificationText(
            // Note: phone number is configured in setup method above.
            user.phoneNumber,
            "en"
        );
        LOG.info("Verification status: {}", verification.getStatus());
        Assertions.assertNotNull(verification.getSid());
        Date afterVerificationDate = new Date();
        Assertions.assertTrue(user.smsConsentDate.getTime() >= beforeVerificationDate.getTime());
        Assertions.assertTrue(user.smsConsentDate.getTime() <= afterVerificationDate.getTime());
    }

    /**
     * Tests whether a verification code can be checked with the Twilio service. Note: if running locally, the {@link
     * #canSendTwilioVerificationText()} test can be run first (with your own mobile phone number) and the code sent to
     * your phone can be used below (in place of 123456) to generate an "approved" status.
     */
    @Test
    public void canCheckSmsVerificationCode() {
        VerificationCheck check = NotificationUtils.checkSmsVerificationCode(
            // Note: phone number is configured in setup method above.
            user.phoneNumber,
            "123456"
        );
        LOG.info("Verification status: {}", check.getStatus());
        Assertions.assertNotNull(check.getSid());
    }
}
