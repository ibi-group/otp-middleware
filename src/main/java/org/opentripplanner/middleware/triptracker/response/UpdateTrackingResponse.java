package org.opentripplanner.middleware.triptracker.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The response provided to the caller when updating tracking of a monitored trip.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateTrackingResponse {

    public String instruction;

    public String tripStatus;

    // Contains the error message when a request fails.
    public String message;

    public UpdateTrackingResponse() {
        // do nothing.
    }

    public UpdateTrackingResponse(String instruction, String tripStatus) {
        this.instruction = instruction;
        this.tripStatus = tripStatus;
    }
}
