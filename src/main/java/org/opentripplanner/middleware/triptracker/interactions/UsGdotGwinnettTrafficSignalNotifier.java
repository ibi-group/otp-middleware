package org.opentripplanner.middleware.triptracker.interactions;

import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Handles notifications to traffic signals managed by
 * US Georgia Department of Transportation in Gwinnett County, GA.
 */
public class UsGdotGwinnettTrafficSignalNotifier implements Interaction {
    private static final Logger LOG = LoggerFactory.getLogger(UsGdotGwinnettTrafficSignalNotifier.class);
    private static final String PED_SIGNAL_CALL_API_HOST = getConfigPropertyAsText("US_GDOT_GWINNETT_PED_SIGNAL_API_HOST");
    private static final String PED_SIGNAL_CALL_API_PATH = getConfigPropertyAsText(
        "US_GDOT_GWINNETT_PED_SIGNAL_API_PATH",
        "/intersections/%s/crossings/%s/call"
    );
    private static final String PED_SIGNAL_CALL_API_KEY = getConfigPropertyAsText("US_GDOT_GWINNETT_PED_SIGNAL_API_KEY");

    public void triggerAction(SegmentAction segmentAction, OtpUser otpUser) {
        String[] idParts = segmentAction.id.split(":");
        String signalId = idParts[0];
        String crossingId = idParts[1];
        triggerPedestrianCall(signalId, crossingId, needsExtendedPhase(otpUser));
    }

    /** Whether a user needs an extended phase or extra time to cross a signaled intersection. */
    public static boolean needsExtendedPhase(OtpUser otpUser) {
        // TODO: criteria for extended phase.
        return otpUser.mobilityProfile.mobilityMode.equalsIgnoreCase("WChairE");
    }

    /**
     * Trigger a pedestrian call for the given traffic signal and given crossing.
     * @param signalId The ID of the targeted traffic signal.
     * @param crossingId The ID of the crossing to activate at the targeted traffic signal.
     */
    public static void triggerPedestrianCall(String signalId, String crossingId, boolean extended) {
        if (PED_SIGNAL_CALL_API_HOST == null || PED_SIGNAL_CALL_API_KEY == null) {
            LOG.error("Not triggering pedestrian call: Host and key were not configured.");
            return;
        }

        String pathAndQuery = PED_SIGNAL_CALL_API_HOST +
            String.format(PED_SIGNAL_CALL_API_PATH, signalId, crossingId) +
            (extended ? "?extended=true" : "");
        try {
            Map<String, String> headers = Map.of("X-API-KEY", PED_SIGNAL_CALL_API_KEY);
            var httpResponse = HttpUtils.httpRequestRawResponse(
                URI.create(pathAndQuery),
                30,
                HttpMethod.POST,
                headers,
                ""
            );
            if (httpResponse.status == 200) {
                LOG.info("Triggered pedestrian call {}", pathAndQuery);
            } else {
                LOG.error("Error {} while triggering pedestrian call", httpResponse.status);
            }
        } catch (Exception e) {
            LOG.error("Could not trigger pedestrian {}", pathAndQuery, e);
        }
    }
}
