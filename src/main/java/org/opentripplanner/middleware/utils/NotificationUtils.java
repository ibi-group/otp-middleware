package org.opentripplanner.middleware.utils;

import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sparkpost.Client;
import com.sparkpost.model.responses.Response;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import com.twilio.type.PhoneNumber;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

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
    private static final String OTP_ADMIN_DASHBOARD_EMAIL = getConfigPropertyAsText("OTP_ADMIN_DASHBOARD_EMAIL");

    /**
     * Send a SMS message to the provided phone number
     * @param toPhone - e.g., +15551234
     * @param body - SMS message body
     * @return messageId if message was sucessful (null otherwise)
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
     * Send a text-only notification email for {@link OtpUser}, ensuring the correct from
     * email address is used (i.e., {@link #FROM_EMAIL}).
     */
    public static boolean sendTextOnlyEmail(
        OtpUser otpUser,
        String subject,
        String text
    ) {
        return sendEmailViaSparkpost(
            FROM_EMAIL,
            otpUser.email,
            subject,
            text,
            null
        );
    }

    /**
     * Send notification email for {@link OtpUser}, ensuring the correct from
     * email address is used (i.e., {@link #FROM_EMAIL}).
     */
    public static boolean sendEmail(
        OtpUser otpUser,
        String subject,
        String textTemplatePath,
        String htmlTemplatePath,
        Object templateData
    ) {
        return sendEmail(
            FROM_EMAIL,
            otpUser.email,
            subject,
            textTemplatePath,
            htmlTemplatePath,
            templateData
        );
    }

    /**
     * Send notification email for {@link AdminUser}, ensuring the correct from
     * email address is used (i.e., {@link #OTP_ADMIN_DASHBOARD_EMAIL}).
     */
    public static boolean sendEmail(
        AdminUser adminUser,
        String subject,
        String textTemplatePath,
        String htmlTemplatePath,
        Object templateData
    ) {
        return sendEmail(
            OTP_ADMIN_DASHBOARD_EMAIL,
            adminUser.email,
            subject,
            textTemplatePath,
            htmlTemplatePath,
            templateData
        );
    }

    /**
     * Helper to send an email that will attempt to render the given templates into a text and html string respectively.
     */
    public static boolean sendEmail(
        String fromEmail,
        String toEmail,
        String subject,
        String textTemplatePath,
        String htmlTemplatePath,
        Object templateData
    ) {
        List<String> renderedData;
        try {
            renderedData = TemplateUtils.renderMultipleTemplatesWithData(
                templateData,
                textTemplatePath,
                htmlTemplatePath
            );
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format(
                    "Failed to render email using text template `%s` and html template `%s`.",
                    textTemplatePath,
                    htmlTemplatePath
                ),
                e
            );
            return false;
        }

        return sendEmailViaSparkpost(
            fromEmail,
            toEmail,
            subject,
            renderedData.get(0),
            renderedData.get(1)
        );
    }

    /**
     * Send notification email using Sparkpost.
     * TODO: determine if we should use sparkpost or sendgrid.
     */
    private static boolean sendEmailViaSparkpost(
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

    /**
     * Send notification email using Sendgrid.
     * TODO: determine if we should use sparkpost or sendgrid.
     */
    public static boolean sendEmailViaSendGrid(String toEmail, String subject, String text, String html) {
        String SENDGRID_API_KEY = getConfigPropertyAsText("SENDGRID_API_KEY");
        if (SENDGRID_API_KEY == null || FROM_EMAIL == null) {
            LOG.error("Notifications disabled due to invalid config. Skipping message to {} SUBJECT: {}", toEmail, subject);
            return false;
        }
        Email from = new Email(FROM_EMAIL);
        Email to = new Email(toEmail);
        Content content = new Content("text/plain", text);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(SENDGRID_API_KEY);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            com.sendgrid.Response response = sg.api(request);
            LOG.info("Message status: {}", response.getStatusCode());
            return response.getStatusCode() < 400;
        } catch (IOException e) {
            LOG.error("Could not send notification to " + to.getEmail(), e);
            // FIXME: bugsnag
            return false;
        }

    }
}

