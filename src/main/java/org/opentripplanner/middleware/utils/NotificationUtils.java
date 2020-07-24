package org.opentripplanner.middleware.utils;

import com.sparkpost.Client;
import com.sparkpost.model.responses.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Created by landon on 4/26/16.
 */
public class NotificationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtils.class);
    private static final String API_KEY = getConfigPropertyAsText("SPARKPOST_KEY");
    private static final String FROM_EMAIL = getConfigPropertyAsText("SPARKPOST_EMAIL");

    public static void sendNotification(String toEmail, String subject, String text, String html) {
        if (API_KEY == null || FROM_EMAIL == null) {
            // Skip sending notification message if notification configuration not configured.
            LOG.warn("Notifications disabled due to invalid config. Skipping message to {} SUBJECT: {}", toEmail, subject);
            return;
        }
        Client client = new Client(API_KEY);
        try {
            Response response = client.sendMessage(FROM_EMAIL, toEmail, subject, text, html);
            LOG.info(response.getResponseMessage());
        } catch (Exception e) {
            // FIXME: Add bugsnag
            LOG.error("Could not send notification to {}", toEmail, e);
        }
    }
}

