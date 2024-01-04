package org.opentripplanner.middleware.triptracker.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import spark.Request;

import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class UpdatedTrackingPayload {

    public String journeyId;

    public List<TrackingLocation> locations = new ArrayList<>();

    /**
     * Get the expected tracking payload for the request.
     */
    public static UpdatedTrackingPayload getPayloadFromRequest(Request request) {
        try {
            return getPOJOFromRequestBody(request, UpdatedTrackingPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON update tracking payload.", e);
            return null;
        }
    }

}
