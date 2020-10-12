package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sparkpost.Client;
import com.sparkpost.model.responses.Response;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import com.twilio.type.PhoneNumber;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

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
    // ISO Country code (or "US", if not provided) for phone number validation with Twilio.
    // See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
    public static final String COUNTRY_CODE = getConfigPropertyAsText("COUNTRY_CODE", "US");
    // From phone must be registered with Twilio account.
    public static final String FROM_PHONE = getConfigPropertyAsText("NOTIFICATION_FROM_PHONE");
    private static final String SPARKPOST_KEY = getConfigPropertyAsText("SPARKPOST_KEY");
    private static final String FROM_EMAIL = getConfigPropertyAsText("NOTIFICATION_FROM_EMAIL");

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
     * Send notification email using Sparkpost.
     * TODO: determine if we should use sparkpost or sendgrid.
     */
    public static boolean sendEmailViaSparkpost(String toEmail, String subject, String text, String html) {
        if (SPARKPOST_KEY == null || FROM_EMAIL == null) {
            LOG.error("Notifications disabled due to invalid config. Skipping message to {} SUBJECT: {}", toEmail, subject);
            return false;
        }
        try {
            Client client = new Client(SPARKPOST_KEY);
            Response response = client.sendMessage(FROM_EMAIL, toEmail, subject, text, html);
            LOG.info("Notification sent to {} status: {}", toEmail, response.getResponseMessage());
            return true;
            // TODO: Is there a more specific exception we're ok with here?
        } catch (Exception e) {
            // FIXME: Add bugsnag
            LOG.error("Could not send notification to {}", toEmail, e);
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

    /**
     * Ensures that the provided phone number is a domestic mobile number.
     * Returns a 400-Bad request status if that is not the case.
     */
    public static com.twilio.rest.lookups.v1.PhoneNumber ensureDomesticPhoneNumber(spark.Request req, String phoneNumberString) {
        if (phoneNumberString.startsWith("+1555555")) {
            // For US fake 555 numbers (used in for UI and backend testing),
            // create and return a PhoneNumber object from a minimal JSON string.
            String lastFourDigits = phoneNumberString.substring("+1555555".length());
            return com.twilio.rest.lookups.v1.PhoneNumber.fromJson(
                "{\n" +
                    "  \"national_format\": \"(555) 555-" + lastFourDigits + "\",\n" +
                    "  \"phone_number\": \"" + phoneNumberString + "\"\n" +
                    "}", new ObjectMapper()
            );
        }

        com.twilio.rest.lookups.v1.PhoneNumber phoneNumber = null;
        try {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            phoneNumber = com.twilio.rest.lookups.v1.PhoneNumber.fetcher(
                new PhoneNumber(phoneNumberString))
                .setCountryCode(COUNTRY_CODE)
                .setType(List.of("carrier"))
                .fetch();
        } catch (ApiException apiException) {
            // Handle 404 response - corresponds to invalid number.
            // In that case we return a 400 bad request response to the requester.
            if (apiException.getStatusCode() == 404) {
                logMessageAndHalt(
                    req,
                    HttpStatus.BAD_REQUEST_400,
                    "Phone number format is invalid."
                );
            } else {
                logMessageAndHalt(
                    req,
                    HttpStatus.INTERNAL_SERVER_ERROR_500,
                    "Error validating phone number format."
                );
            }
        } catch (Exception e) {
            logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error validating phone number format."
            );
        }

        if (phoneNumber != null) {
            System.out.println(phoneNumber.getPhoneNumber());
            System.out.println(phoneNumber.getCarrier().get("type"));
            System.out.println(phoneNumber.getCarrier().get("name"));
            System.out.println(phoneNumber.getCountryCode());
            System.out.println(phoneNumber.getNationalFormat());

            // Reject numbers that are international with respect to COUNTRY_CODE.
            // TODO: Also reject numbers whose .getCarrier().get("type") is not "mobile"?
            if (!phoneNumber.getCountryCode().equals(COUNTRY_CODE)) {
                logMessageAndHalt(
                    req,
                    HttpStatus.BAD_REQUEST_400,
                    "Phone number must be domestic."
                );
            }

            return phoneNumber;
        }

        return null;
    }
}

