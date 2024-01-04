package org.opentripplanner.middleware.triptracker.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class ForceEndTrackingPayload {

    public String tripId;

    /**
     * Get the expected tracking payload for the request.
     */
    public static ForceEndTrackingPayload getPayloadFromRequest(Request request) {
        try {
            return getPOJOFromRequestBody(request, ForceEndTrackingPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON force end tracking payload.", e);
            return null;
        }
    }

}
