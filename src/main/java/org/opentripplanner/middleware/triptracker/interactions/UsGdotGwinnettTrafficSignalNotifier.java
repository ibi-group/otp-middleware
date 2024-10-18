package org.opentripplanner.middleware.triptracker.interactions;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.AtomicAvailability;
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
    private static final String PED_SIGNAL_CALL_API_HOST = getConfigPropertyAsText(
        "US_GDOT_GWINNETT_PED_SIGNAL_API_HOST"
    );
    private static final String PED_SIGNAL_CALL_API_PATH = getConfigPropertyAsText(
        "US_GDOT_GWINNETT_PED_SIGNAL_API_PATH",
        "/intersections/%s/crossings/%s/call"
    );
    private static final String PED_SIGNAL_CALL_API_KEY = getConfigPropertyAsText(
        "US_GDOT_GWINNETT_PED_SIGNAL_API_KEY"
    );

    private static final AtomicAvailability availability = new AtomicAvailability();

    private final String host;
    private final String path;
    private final String key;
    private final boolean isTesting;

    public UsGdotGwinnettTrafficSignalNotifier() {
        host = PED_SIGNAL_CALL_API_HOST;
        path = PED_SIGNAL_CALL_API_PATH;
        key = PED_SIGNAL_CALL_API_KEY;
        isTesting = false;
    }

    public UsGdotGwinnettTrafficSignalNotifier(String host, String path, String key) {
        this.host = host;
        this.path = path;
        this.key = key;
        this.isTesting = true;
    }

    @Override
    public void triggerAction(SegmentAction segmentAction, OtpUser otpUser) {
        String[] idParts = segmentAction.id.split(":");
        String signalId = idParts[0];
        String crossingId = idParts[1];
        triggerPedestrianCall(signalId, crossingId, needsExtendedPhase(otpUser));
    }

    /** Whether a user needs an extended phase or extra time to cross a signaled intersection. */
    public static boolean needsExtendedPhase(OtpUser otpUser) {
        MobilityProfile profile = otpUser.mobilityProfile;
        if (profile == null) return false;

        String mode = profile.mobilityMode;
        return mode != null && !mode.equalsIgnoreCase("None");
    }

    public String getUrl(String signalId, String crossingId, boolean extended) {
        return host + String.format(path, signalId, crossingId) + (extended ? "?extended=true" : "");
    }

    public Map<String, String> getHeaders() {
        return Map.of("X-API-KEY", key);
    }

    /**
     * Trigger a pedestrian call for the given traffic signal and given crossing.
     * @param signalId The ID of the targeted traffic signal.
     * @param crossingId The ID of the crossing to activate at the targeted traffic signal.
     */
    public boolean triggerPedestrianCall(String signalId, String crossingId, boolean extended) {
        if (host == null || key == null) {
            LOG.error("Not triggering pedestrian call: Host and key are not configured.");
            return false;
        }

        if (availability.claim()) {
            try (availability) {
                if (isTesting) {
                    // For testing, just wait so that other calls can be attempted.
                    Thread.sleep(2000);
                    return true;
                } else {
                    String pathAndQuery = getUrl(signalId, crossingId, extended);
                    var httpResponse = HttpUtils.httpRequestRawResponse(
                        URI.create(pathAndQuery),
                        30,
                        HttpMethod.POST,
                        getHeaders(),
                        ""
                    );
                    if (httpResponse.status == HttpStatus.OK_200) {
                        LOG.info("Triggered pedestrian call {}", pathAndQuery);
                        return true;
                    } else {
                        LOG.error("Error {} while triggering pedestrian call {}", httpResponse.status, pathAndQuery);
                    }
                }
            } catch (InterruptedException e) {
                // Continue with the rest.
            }
        }
        return false;
    }
}
