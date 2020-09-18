package org.opentripplanner.middleware;

import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;

/**
 * Contains tests for the various notification utilities to send SMS and email messages. Note: these tests require the
 * environment variables RUN_E2E=true and valid values for TEST_TO_EMAIL and TEST_TO_PHONE. Furthermore, TEST_TO_PHONE
 * must be a verified phone number in a valid Twilio account.
 */
public class NotificationUtilsTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtilsTest.class);
    private static OtpUser user;

    @BeforeAll
    public static void setup() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        // Note: In order to run the notification tests, these values must be provided in in system
        // environment variables, which can be defined in a run configuration in your IDE.
        String email = System.getenv("TEST_TO_EMAIL");
        // Phone must be in the form "+15551234" and must be verified first in order to send notifications
        String phone = System.getenv("TEST_TO_PHONE");
        user = createUser(email, phone);
    }

    @AfterAll
    public static void tearDown() {
        if (user != null) Persistence.otpUsers.removeById(user.id);
    }

    @Test
    public void canSendSparkpostEmailNotification() {
        boolean success = NotificationUtils.sendEmailViaSparkpost(user.email, "Hi there", "This is the body", null);
        Assertions.assertTrue(success);
    }

    @Test
    public void canSendSendGridEmailNotification() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        boolean success = NotificationUtils.sendEmailViaSendGrid(
            user.email,
            "Hi there",
            "This is the body",
            null
        );
        Assertions.assertTrue(success);
    }

    @Test
    public void canSendSmsNotification() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
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
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        Verification verification = NotificationUtils.sendVerificationText(
            // Note: phone number is configured in setup method above.
            user.phoneNumber
        );
        LOG.info("Verification status: {}", verification.getStatus());
        Assertions.assertNotNull(verification.getSid());
    }

    /**
     * Tests whether a verification code can be checked with the Twilio service. Note: if running locally, the {@link
     * #canSendTwilioVerificationText()} test can be run first (with your own mobile phone number) and the code sent to
     * your phone can be used below (in place of 123456) to generate an "approved" status.
     */
    @Test
    public void canCheckSmsVerificationCode() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));

        VerificationCheck check = NotificationUtils.checkSmsVerificationCode(
            // Note: phone number is configured in setup method above.
            user.phoneNumber,
            "123456"
        );
        LOG.info("Verification status: {}", check.getStatus());
        Assertions.assertNotNull(check.getSid());
    }
}
