package org.opentripplanner.middleware.bugsnag;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.util.Set;

public class BugsnagWebhook {
    private static final Logger LOG = LoggerFactory.getLogger(BugsnagWebhook.class);

    private static final Set<String> BUGSNAG_WEBHOOK_PERMITTED_IPS =
        ConfigUtils.getConfigPropertyAsStringSet("BUGSNAG_WEBHOOK_PERMITTED_IPS");

    /**
     * Extract Bugsnag project error from webhook delivery.
     */
    public static void processWebHookDelivery(Request request) {
        if (BUGSNAG_WEBHOOK_PERMITTED_IPS == null) {
            LOG.warn("Bugsnag webhook permitted IPs not defined. Caller IP not validated nor content processed.");
            return;
        } else if(!BUGSNAG_WEBHOOK_PERMITTED_IPS.contains(request.ip())) {
            LOG.warn("Bugsnag webhook delivery called from unauthorized IP: {}. Request rejected.", request.ip());
            return;
        }
        try {
            BugsnagWebHookDelivery webHookDelivery =
                JsonUtils.getPOJOFromJSON(request.body(), BugsnagWebHookDelivery.class);
            if (webHookDelivery != null) {
                LOG.info("New event delivered via the Bugsnag webhook. Storing and notifying subscribed admin users.");
                Persistence.bugsnagEvents.create(new BugsnagEvent(webHookDelivery));
                // Notify any subscribed users about new events.
                BugsnagReporter.sendEmailForEvents(1);
            }
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag("Failed to parse webhook delivery!", request.body(), e);
        }
    }
}
