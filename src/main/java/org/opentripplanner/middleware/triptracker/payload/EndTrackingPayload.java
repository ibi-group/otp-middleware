package org.opentripplanner.middleware.triptracker.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class EndTrackingPayload {

    public String journeyId;

    /**
     * Get the expected tracking payload for the request.
     */
    public static EndTrackingPayload getPayloadFromRequest(Request request) {
        try {
            return getPOJOFromRequestBody(request, EndTrackingPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON end tracking payload.", e);
            return null;
        }
    }

}
