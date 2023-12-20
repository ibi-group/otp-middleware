package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
