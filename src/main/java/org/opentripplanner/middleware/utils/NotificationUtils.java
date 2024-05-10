package org.opentripplanner.middleware.utils;

import com.google.gson.Gson;
import com.sparkpost.Client;
import com.sparkpost.model.responses.Response;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import com.twilio.type.PhoneNumber;
import freemarker.template.TemplateException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.StringUtil;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This class contains utils for sending SMS, email, and push notifications.
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
    private static final String PUSH_API_KEY = getConfigPropertyAsText("PUSH_API_KEY");
    private static final String PUSH_API_URL = getConfigPropertyAsText("PUSH_API_URL");

    /**
     * Although SMS are 160 characters long and Twilio supports sending up to 1600 characters,
     * they don't recommend sending more than 320 characters in a single "request"
     * to not inundate users with long messages and to reduce cost
     * (messages above 160 characters are split into multiple SMS that are billed individually).
     * See https://support.twilio.com/hc/en-us/articles/360033806753-Maximum-Message-Length-with-Twilio-Programmable-Messaging
     */
    private static final int SMS_MAX_LENGTH = 320;
    /**
     * The most restrictive content length (title and message)
     * between Android (240 for message) and iOS (178 for title and message).
     */
    public static final int PUSH_TOTAL_MAX_LENGTH = 178;
    /** The most restrictive title length between Android (65) and iOS (none). */
    public static final int PUSH_TITLE_MAX_LENGTH = 65;

    /**
     * @param otpUser  target user
     * @param textTemplate  template to use for email in text format
     * @param templateData  template data
     */
    public static String sendPush(OtpUser otpUser, String textTemplate, Object templateData, String tripName, String tripId) {
        // If Push API config properties aren't set, do nothing.
        if (PUSH_API_KEY == null || PUSH_API_URL == null) return null;
        try {
            String body = TemplateUtils.renderTemplate(textTemplate, templateData);
            String toUser = otpUser.email;
            return otpUser.pushDevices > 0 ? sendPush(toUser, body, tripName, tripId) : "OK";
        } catch (TemplateException | IOException e) {
            // This catch indicates there was an error rendering the template. Note: TemplateUtils#renderTemplate
            // handles Bugsnag reporting/error logging, so that is not needed here.
            return null;
        }
    }

    /**
     * Send a push notification message to the provided user
     * @param toUser    user account ID (email address)
     * @param body      message body
     * @param tripId    Monitored trip ID
     * @return          "OK" if message was successful (null otherwise)
     */
    static String sendPush(String toUser, String body, String tripName, String tripId) {
        try {
            NotificationInfo notifInfo = new NotificationInfo(
                toUser,
                body,
                tripName,
                tripId
            );
            var jsonBody = new Gson().toJson(notifInfo);
            Map<String, String> headers = Map.of(
                "Accept", "application/json",
                "Content-Type", "application/json"
            );
            var httpResponse = HttpUtils.httpRequestRawResponse(
                URI.create(PUSH_API_URL + "/notification/publish?api_key=" + PUSH_API_KEY),
                1000,
                HttpMethod.POST,
                headers,
                jsonBody
            );
            if (httpResponse.status == 200) {
                return "OK";
            } else {
                LOG.error("Error {} while trying to initiate push notification", httpResponse.status);
            }
        } catch (Exception e) {
            LOG.error("Could not initiate push notification", e);
        }
        return null;
    }

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
                // Trim body to max message length
                StringUtil.truncate(body, SMS_MAX_LENGTH)
            ).create();
            LOG.info("SMS ({}) sent successfully", message.getSid());
            return message.getSid();
            // TODO: Is there a more specific exception we're ok with here?
        } catch (Exception e) {
            LOG.error("Could not create SMS", e);
            return null;
            // FIXME bugsnag
        }
    }

    /**
     * Get a supported Twilio locale for a given locale in IETF's BPC 47 format.
     * See https://www.twilio.com/docs/verify/supported-languages#verify-default-template
     */
    public static String getTwilioLocale(String locale) {
        if (locale == null) {
            return "en";
        }
        // The Twilio's supported locales are just the first two letters of the user's locale,
        // unless it is zh-HK, pt-BR, or en-GB.
        switch (locale) {
            case "en-GB":
            case "pt-BR":
            case "zh-HK":
                return locale;
            default:
                return locale.length() < 2 ? "en" : locale.substring(0, 2);
        }
    }

    /**
     * Send verification text to phone number (i.e., a code that the recipient will use to verify ownership of the
     * number via the OTP web app).
     */
    public static Verification sendVerificationText(String phoneNumber, String locale) {
        if (TWILIO_ACCOUNT_SID == null || TWILIO_AUTH_TOKEN == null) {
            LOG.error("SMS notifications not configured correctly.");
            return null;
        }
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            VerificationCreator smsVerifier = Verification.creator(TWILIO_VERIFICATION_SERVICE_SID, phoneNumber, "sms");
            smsVerifier.setLocale(getTwilioLocale(locale));
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

    /**
     * Get number of push notification devices. Calls Push API's <code>get</code> endpoint, the only reliable way
     * to obtain this value, as the <code>publish</code> endpoint returns success even for zero devices.
     *
     * @param toUser  email address of user that devices are indexed by
     * @return number of devices registered, <code>0</code> can mean zero devices or an error obtaining the number
     */
    public static int getPushInfo(String toUser) {
        // If Push API config properties aren't set, no info can be obtained.
        if (PUSH_API_KEY == null || PUSH_API_URL == null) return 0;
        try {
            Map<String, String> headers = Map.of("Accept", "application/json");
            var httpResponse = HttpUtils.httpRequestRawResponse(
                URI.create(getPushDevicesUrl(String.format(
                    "%s/devices/get?api_key=%s&user=",
                    PUSH_API_URL,
                    PUSH_API_KEY
                ), toUser)),
                1000,
                HttpMethod.GET,
                headers,
                null
            );
            if (httpResponse.status == 200) {
                // We don't use any of this information, we only care how many devices are registered.
                var devices = JsonUtils.getPOJOFromHttpBodyAsList(httpResponse, Object.class);
                return devices.size();
            } else {
                LOG.error("Error {} while getting info on push notification devices", httpResponse.status);
            }
        } catch (Exception e) {
            LOG.error("No info on push notification devices", e);
        }
        return 0;
    }

    static String getPushDevicesUrl(String baseUrl, String toUser) {
        return baseUrl + URLEncoder.encode(toUser, UTF_8);
    }

    /**
     * Poll the push middleware for the number of devices registered to receive push notifications
     * for the specified user, and update the corresponding field in memory and Mongo.
     * @param otpUser The {@link OtpUser} for which to check and update push devices.
     */
    public static void updatePushDevices(OtpUser otpUser) {
        int numPushDevices = getPushInfo(otpUser.email);
        if (numPushDevices != otpUser.pushDevices) {
            otpUser.pushDevices = numPushDevices;
            Persistence.otpUsers.replace(otpUser.id, otpUser);
      	}
    }

    static class NotificationInfo {
        public final String user;
        public final String message;
        public final String title;
        public final String tripId;

        public NotificationInfo(String user, String message, String title, String tripId) {
            String truncatedTitle = StringUtil.truncate(title, PUSH_TITLE_MAX_LENGTH);
            int truncatedMessageLength = PUSH_TOTAL_MAX_LENGTH - truncatedTitle.length();

            this.user = user;
            this.title = truncatedTitle;
            this.message = StringUtil.truncate(message, truncatedMessageLength);
            this.tripId = tripId;
        }
    }
}
