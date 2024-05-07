package org.opentripplanner.middleware.triptracker.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The response provided to the caller when starting or updating tracking of a monitored trip.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackingResponse {

    public int frequencySeconds;

    public String instruction;

    public String journeyId;

    public String tripStatus;

    // Contains the error message when a request fails.
    public String message;

    public TrackingResponse() {
        // do nothing.
    }

    public TrackingResponse(String instruction, String tripStatus) {
        this.instruction = instruction;
        this.tripStatus = tripStatus;
    }

    public TrackingResponse(int frequencySeconds, String instruction, String journeyId, String tripStatus) {
        this.frequencySeconds = frequencySeconds;
        this.instruction = instruction;
        this.journeyId = journeyId;
        this.tripStatus = tripStatus;
    }
}