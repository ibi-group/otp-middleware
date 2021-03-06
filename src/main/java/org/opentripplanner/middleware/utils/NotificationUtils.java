package org.opentripplanner.middleware.utils;

import com.sparkpost.Client;
import com.sparkpost.model.responses.Response;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import com.twilio.type.PhoneNumber;
import freemarker.template.TemplateException;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This class contains utils for sending SMS and email notifications.
 *
 * TODO: It may be better to initialize all of these notification clients in a static block? This may not really be
 *  necessary though -- needs some more research.
 */
public class NotificationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtils.class);
    // Find your Account Sid and Token at https://twilio.com/user/account
    public static final String TWILIO_ACCOUNT_SID = getConfigPropertyAsText("TWILIO_ACCOUNT_SID");
    public static final String TWILIO_AUTH_TOKEN = getConfigPropertyAsText("TWILIO_AUTH_TOKEN");
    public static final String TWILIO_VERIFICATION_SERVICE_SID = getConfigPropertyAsText("TWILIO_VERIFICATION_SERVICE_SID");
    // From phone must be registered with Twilio account.
    public static final String FROM_PHONE = getConfigPropertyAsText("NOTIFICATION_FROM_PHONE");
    private static final String SPARKPOST_KEY = getConfigPropertyAsText("SPARKPOST_KEY");
    private static final String FROM_EMAIL = getConfigPropertyAsText("NOTIFICATION_FROM_EMAIL");
    public static final String OTP_ADMIN_DASHBOARD_FROM_EMAIL = getConfigPropertyAsText("OTP_ADMIN_DASHBOARD_FROM_EMAIL");

    /**
     * Send templated SMS to {@link OtpUser}'s verified phone number.
     * @param otpUser       target user
     * @param smsTemplate   template to use for SMS message
     * @param templateData          template data
     * @return              messageId if message was successful (null otherwise)
     */
    public static String sendSMS(OtpUser otpUser, String smsTemplate, Object templateData) {
        if (!otpUser.isPhoneNumberVerified) {
            LOG.error("Cannot send SMS to unverified user ({})!", otpUser.email);
            return null;
        }
        try {
            String body = TemplateUtils.renderTemplate(smsTemplate, templateData);
            return sendSMS(otpUser.phoneNumber, body);
        } catch (TemplateException | IOException e) {
            // This catch indicates there was an error rendering the template. Note: TemplateUtils#renderTemplate
            // handles Bugsnag reporting/error logging, so that is not needed here.
            return null;
        }
    }

    /**
     * Send a SMS message to the provided phone number.
     * @param toPhone   e.g., +15551234
     * @param body      SMS message body
     * @return          messageId if message was successful (null otherwise)
     */
    public static String sendSMS(String toPhone, String body) {
        if (TWILIO_ACCOUNT_SID == null || TWILIO_AUTH_TOKEN == null) {
            LOG.error("SMS notifications not configured correctly.");
            return null;
        }
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            PhoneNumber fromPhoneNumber = new PhoneNumber(FROM_PHONE);
            PhoneNumber toPhoneNumber = new PhoneNumber(toPhone);
            Message message = Message.creator(
                toPhoneNumber,
                fromPhoneNumber,
                body
            ).create();
            LOG.debug("SMS ({}) sent successfully", message.getSid());
            return message.getSid();
            // TODO: Is there a more specific exception we're ok with here?
        } catch (Exception e) {
            LOG.error("Could not create SMS", e);
            return null;
            // FIXME bugsnag
        }
    }

    /**
     * Send verification text to phone number (i.e., a code that the recipient will use to verify ownership of the
     * number via the OTP web app).
     */
    public static Verification sendVerificationText(String phoneNumber) {
        if (TWILIO_ACCOUNT_SID == null || TWILIO_AUTH_TOKEN == null) {
            LOG.error("SMS notifications not configured correctly.");
            return null;
        }
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            VerificationCreator smsVerifier = Verification.creator(TWILIO_VERIFICATION_SERVICE_SID, phoneNumber, "sms");
            Verification verification = smsVerifier.create();
            LOG.info("SMS verification ({}) sent successfully", verification.getSid());
            return verification;
            // TODO: Is there a more specific exception we're ok with here?
        } catch (Exception e) {
            LOG.error("Could not send SMS verification", e);
            return null;
            // FIXME bugsnag
        }
    }

    /**
     * Check that an SMS verification code (e.g., 123456) is valid for the given phone number (+15551234).
     */
    public static VerificationCheck checkSmsVerificationCode(String phoneNumber, String code) {
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            VerificationCheck check = VerificationCheck.creator(TWILIO_VERIFICATION_SERVICE_SID, code)
                .setTo(phoneNumber)
                .create();
            return check;
        } catch (Exception e) {
            // FIXME bugsnag
            LOG.error("Could not check status of SMS verification code", e);
            return null;
        }
    }

    /**
     * Send notification email to {@link OtpUser}, ensuring the correct from
     * email address is used (i.e., {@link #FROM_EMAIL}).
     */
    public static boolean sendEmail(
        OtpUser otpUser,
        String subject,
        String textTemplate,
        String htmlTemplate,
        Object templateData
    ) {
        return sendEmail(FROM_EMAIL, otpUser.email, subject, textTemplate, htmlTemplate, templateData);
    }

    /**
     * Send notification email to {@link AdminUser}, ensuring the correct from
     * email address is used (i.e., {@link #OTP_ADMIN_DASHBOARD_FROM_EMAIL}).
     */
    public static boolean sendEmail(
        AdminUser adminUser,
        String subject,
        String textTemplate,
        String htmlTemplate,
        Object templateData
    ) {
        return sendEmail(OTP_ADMIN_DASHBOARD_FROM_EMAIL, adminUser.email, subject, textTemplate, htmlTemplate, templateData);
    }

    /**
     * Send templated email using SparkPost.
     * @param fromEmail     from email address
     * @param toEmail       recipient email address
     * @param subject       email subject liine
     * @param textTemplate  template to use for email in text format
     * @param htmlTemplate  template to use for email in HTML format
     * @param templateData          template data
     * @return              whether the email was sent successfully
     */
    private static boolean sendEmail(
        String fromEmail,
        String toEmail,
        String subject,
        String textTemplate,
        String htmlTemplate,
        Object templateData
    ) {
        try {
            String text = TemplateUtils.renderTemplate(textTemplate, templateData);
            String html = TemplateUtils.renderTemplate(htmlTemplate, templateData);
            return sendEmailViaSparkpost(fromEmail, toEmail, subject, text, html);
        } catch (TemplateException | IOException e) {
            // This catch indicates there was an error rendering the template. Note: TemplateUtils#renderTemplate
            // handles Bugsnag reporting/error logging, so that is not needed here.
            return false;
        }
    }

    /**
     * Send notification email using Sparkpost.
     */
    public static boolean sendEmailViaSparkpost(
        String fromEmail,
        String toEmail,
        String subject,
        String text,
        String html
    ) {
        if (SPARKPOST_KEY == null) {
            LOG.error("Notifications disabled due to missing SPARKPOST_KEY. Skipping message to {} SUBJECT: {}", toEmail, subject);
            return false;
        }
        if (fromEmail == null) {
            LOG.error("Notification skipped due to invalid FROM email (check config). Skipping message to {} SUBJECT: {}", toEmail, subject);
            return false;
        }
        if (text == null && html == null) {
            LOG.error("Notification skipped due to empty text and html bodies");
            return false;
        }
        try {
            Client client = new Client(SPARKPOST_KEY);
            Response response = client.sendMessage(fromEmail, toEmail, subject, text, html);
            LOG.info("Notification sent to {} status: {}", toEmail, response.getResponseMessage());
            return true;
            // TODO: Is there a more specific exception we're ok with here?
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format("Could not send notification to %s", toEmail),
                e
            );
            return false;
        }
    }
}

