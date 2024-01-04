package org.opentripplanner.middleware.triptracker.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Trip tracking payload that covers the expect parameters for starting a tracked trip.
 */
public class StartTrackingPayload {

    public TrackingLocation location;

    public String tripId;

    /**
     * Get the expected tracking payload from the request.
     */
    public static StartTrackingPayload getPayloadFromRequest(Request request) {
        try {
            return getPOJOFromRequestBody(request, StartTrackingPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON start tracking payload.", e);
            return null;
        }
    }

}
