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
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This class contains utils for sending SMS and email notifications.
 */
public class NotificationUtils {
    // Find your Account Sid and Token at https://twilio.com/user/account
    public static final String ACCOUNT_SID = getConfigPropertyAsText("TWILIO_ACCOUNT_SID");
    public static final String AUTH_TOKEN = getConfigPropertyAsText("TWILIO_AUTH_TOKEN");
    // From phone must be registered with Twilio account.
    public static final String FROM_PHONE = getConfigPropertyAsText("NOTIFICATION_FROM_PHONE");
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtils.class);
    private static final String SPARKPOST_KEY = getConfigPropertyAsText("SPARKPOST_KEY");
    private static final String FROM_EMAIL = getConfigPropertyAsText("NOTIFICATION_FROM_EMAIL");

    /**
     * Send a SMS message to the provided phone number
     * @param toPhone - e.g., +15551234
     * @param body - SMS message body
     * @return messageId if message was sucessful (null otherwise)
     */
    public static String sendSMS(String toPhone, String body) {
        if (ACCOUNT_SID == null || AUTH_TOKEN == null) {
            LOG.warn("SMS notifications not configured correctly.");
            return null;
        }
        try {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            PhoneNumber fromPhoneNumber = new PhoneNumber(FROM_PHONE);
            PhoneNumber toPhoneNumber = new PhoneNumber(toPhone);
            Message message = Message.creator(
                toPhoneNumber,
                fromPhoneNumber,
                body
            ).create();
            LOG.debug("SMS ({}) sent successfully", message.getSid());
            return message.getSid();
        } catch (Exception e) {
            LOG.error("Could not create SMS", e);
            return null;
            // FIXME bugsnag
        }
    }

    /**
     * Send notification email using Sparkpost.
     * TODO: determin if we should use sparkpost or sendgrid.
     */
    public static void sendEmail(String toEmail, String subject, String text, String html) {
        if (SPARKPOST_KEY == null || FROM_EMAIL == null) {
            LOG.warn("Notifications disabled due to invalid config. Skipping message to {} SUBJECT: {}", toEmail, subject);
            return;
        }
        Client client = new Client(SPARKPOST_KEY);
        try {
            Response response = client.sendMessage(FROM_EMAIL, toEmail, subject, text, html);
            LOG.info(response.getResponseMessage());
        } catch (Exception e) {
            // FIXME: Add bugsnag
            LOG.error("Could not send notification to {}", toEmail, e);
        }
    }

    /**
     * Send notification email using Sendgrid.
     * TODO: determin if we should use sparkpost or sendgrid.
     */
    public static boolean sendSendGridEmail(String toEmail, String subject, String text, String html) {
        String SENDGRID_API_KEY = getConfigPropertyAsText("SENDGRID_API_KEY");
        if (SENDGRID_API_KEY == null || FROM_EMAIL == null) {
            LOG.warn("Notifications disabled due to invalid config. Skipping message to {} SUBJECT: {}", toEmail, subject);
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

