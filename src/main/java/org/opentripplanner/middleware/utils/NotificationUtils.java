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
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.StringUtil;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.Device;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.i18n.Message.TRIP_EMAIL_SUBJECT;
import static org.opentripplanner.middleware.i18n.Message.TRIP_EMAIL_SUBJECT_FOR_USER;
import static org.opentripplanner.middleware.i18n.Message.TRIP_SURVEY_NOTIFICATION;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip.SETTINGS_PATH;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.I18nUtils.getOtpUserLocale;
import static org.opentripplanner.middleware.utils.I18nUtils.label;

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
    private static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");
    private static final String TRIP_SURVEY_SUBDOMAIN = getConfigPropertyAsText("TRIP_SURVEY_SUBDOMAIN");
    private static final String OTP_UI_NAME = ConfigUtils.getConfigPropertyAsText("OTP_UI_NAME");
    private static final String OTP_UI_URL = ConfigUtils.getConfigPropertyAsText("OTP_UI_URL");

    public enum UserType {
        COMPANION,
        OBSERVER,
        PRIMARY_TRAVELER
    }

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
            return otpUser.pushDevices > 0 ? sendPush(otpUser, body, tripName, tripId, null, null, null) : "OK";
        } catch (TemplateException | IOException e) {
            // This catch indicates there was an error rendering the template. Note: TemplateUtils#renderTemplate
            // handles Bugsnag reporting/error logging, so that is not needed here.
            return null;
        }
    }

    /**
     * @param otpUser  target user
     * @param trip  Trip about which the survey notification is about.
     * @param notificationId  Notification ID
     */
    public static String sendTripSurveyPush(OtpUser otpUser, MonitoredTrip trip, String notificationId) {
        // Check devices first - No devices returns OK (favors E2E testing)
        if (otpUser.pushDevices == 0) return "OK";

        // If Push API/survey config properties aren't set, do nothing (will trigger warning log).
        if (
            Strings.isBlank(PUSH_API_KEY) ||
            Strings.isBlank(PUSH_API_URL) ||
            Strings.isBlank(TRIP_SURVEY_ID) ||
            Strings.isBlank(TRIP_SURVEY_SUBDOMAIN)
        ) {
            return null;
        }

        Locale locale = I18nUtils.getOtpUserLocale(otpUser);
        String tripTime = DateTimeUtils.formatShortDate(trip.itinerary.startTime, locale);
        String body = String.format(TRIP_SURVEY_NOTIFICATION.get(locale), tripTime);
        return sendPush(otpUser, body, trip.tripName, trip.id, TRIP_SURVEY_ID, TRIP_SURVEY_SUBDOMAIN, notificationId);
    }

    /**
     * Send a push notification message to the provided user
     * @param toUser    user account ID (email address)
     * @param body      message body
     * @param tripName  Monitored trip name to show in notification title
     * @param tripId    Monitored trip ID
     * @param surveyId  Survey ID
     * @param notificationId  Notification ID
     * @return          "OK" if message was successful (null otherwise)
     */
    static String sendPush(
        OtpUser toUser,
        String body,
        String tripName,
        String tripId,
        String surveyId,
        String surveySubdomain,
        String notificationId
    ) {
        try {
            NotificationInfo notificationInfo = new NotificationInfo(
                notificationId,
                toUser,
                body,
                tripName,
                tripId,
                surveyId,
                surveySubdomain
            );
            var jsonBody = new Gson().toJson(notificationInfo);
            var httpResponse = HttpUtils.httpRequestRawResponse(
                URI.create(PUSH_API_URL + "/notification/publish?api_key=" + PUSH_API_KEY),
                1000,
                HttpMethod.POST,
                HttpUtils.HEADERS_JSON,
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
                return getNumberOfUniqueDevices(
                    Objects.requireNonNull(JsonUtils.getPOJOFromHttpBodyAsList(httpResponse, Device.class))
                );
            } else {
                LOG.error("Error {} while getting info on push notification devices", httpResponse.status);
            }
        } catch (Exception e) {
            LOG.error("No info on push notification devices", e);
        }
        return 0;
    }

    /**
     * Return the number of unique, non null, device names.
     */
    public static int getNumberOfUniqueDevices(List<Device> devices) {
        return devices
            .stream()
            .map(Device::getDeviceName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet())
            .size();
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

    /**
     * Gets the localized subject line for a trip notification.
     */
    public static String getTripEmailSubject(OtpUser otpUser, Locale locale, MonitoredTrip trip) {
        return trip.tripName != null
            ? String.format(TRIP_EMAIL_SUBJECT.get(locale), trip.tripName)
            : String.format(TRIP_EMAIL_SUBJECT_FOR_USER.get(locale), otpUser.email);
    }

    /**
     * Replaces the sender display name with the specified user's name (fallback to the user's email).
     */
    public static String replaceUserNameInFromEmail(String fromEmail, OtpUser otpUser) {
        int firstBracketIndex = fromEmail.indexOf('<');
        int lastBracketIndex = fromEmail.indexOf('>');
        String displayedName = Strings.isBlank(otpUser.name) ? otpUser.email : otpUser.name;
        return String.format("%s %s", displayedName, fromEmail.substring(firstBracketIndex, lastBracketIndex + 1));
    }

    public static void notifyCompanion(MonitoredTrip monitoredTrip, OtpUser companionUser, UserType userType) {
        if (companionUser != null) {
            Locale locale = getOtpUserLocale(companionUser);
            String tripLinkLabel = org.opentripplanner.middleware.i18n.Message.TRIP_LINK_TEXT.get(locale);
            String tripUrl = monitoredTrip.getTripUrl();

            OtpUser tripCreator = Persistence.otpUsers.getById(monitoredTrip.userId);

            String greeting;
            switch (userType) {
                case COMPANION:
                    greeting = "%s added you as a companion on their trip:";
                    break;
                case PRIMARY_TRAVELER:
                    greeting = "%s made you the primary traveler on this trip:";
                    break;
                case OBSERVER:
                default:
                    greeting = "%s added you as an observer for their trip:";
                    break;
            }

            // TODO: finish i18n
            sendEmail(
                replaceUserNameInFromEmail(FROM_EMAIL, Persistence.otpUsers.getById(monitoredTrip.userId)),
                companionUser.email,
                getTripEmailSubject(companionUser, locale, monitoredTrip),
                "ShareTripText.ftl", // TODO: See if msg body can be reused
                "ShareTripHtml.ftl",
                Map.of(
                    "emailGreeting", String.format(
                        greeting,
                        tripCreator.email
                    ),
                    "tripUrl", tripUrl,
                    "tripLinkAnchorLabel", tripLinkLabel,
                    "tripLinkLabelAndUrl", label(tripLinkLabel, tripUrl, locale),
                    "emailFooter", String.format(org.opentripplanner.middleware.i18n.Message.TRIP_EMAIL_FOOTER.get(locale), OTP_UI_NAME),
                    "manageLinkText", org.opentripplanner.middleware.i18n.Message.TRIP_EMAIL_MANAGE_NOTIFICATIONS.get(locale),
                    "manageLinkUrl", String.format("%s%s", OTP_UI_URL, SETTINGS_PATH)
                )
            );
        }
    }

    static class NotificationInfo {
        /** ID for tracking notifications and survey responses. */
        public final String notificationId;

        /** In reality, the email of the desired user (the push service we use looks up users by email) */
        public final String user;

        /** The Mongo ID of the desired user */
        public final String userId;

        /** The message shown in the notification body */
        public final String message;

        /** The title of this notification */
        public final String title;

        /** The ID of the trip associated to this notification */
        public final String tripId;

        /** The ID of the survey to be launched for said trip, if applicable. */
        public final String surveyId;

        /** The subdomain of the website where the survey is administered, if applicable. */
        public final String surveySubdomain;

        public NotificationInfo(String notificationId, OtpUser user, String message, String title, String tripId) {
            this(notificationId, user, message, title, tripId, null, null);
        }

        public NotificationInfo(
            String notificationId,
            OtpUser user,
            String message,
            String title,
            String tripId,
            String surveyId,
            String surveySubdomain
        ) {
            String truncatedTitle = StringUtil.truncate(title, PUSH_TITLE_MAX_LENGTH);
            int truncatedMessageLength = PUSH_TOTAL_MAX_LENGTH - truncatedTitle.length();

            this.notificationId = notificationId;
            this.user = user.email;
            this.userId = user.id;
            this.title = truncatedTitle;
            this.message = StringUtil.truncate(message, truncatedMessageLength);
            this.tripId = tripId;
            this.surveyId = surveyId;
            this.surveySubdomain = surveySubdomain;
        }
    }
}
