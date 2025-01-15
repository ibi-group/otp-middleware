package org.opentripplanner.middleware.triptracker.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.opentripplanner.middleware.otp.response.Itinerary;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerouteResponse extends TrackingResponse {

    Itinerary itinerary;

    public RerouteResponse() {
    }

    public RerouteResponse(int frequencySeconds, String instruction, String journeyId, String tripStatus, Itinerary itinerary) {
        super(frequencySeconds, instruction, journeyId, tripStatus);
        this.itinerary = itinerary;
    }

    public RerouteResponse(TrackingResponse trackingResponse, Itinerary itinerary) {
        super(trackingResponse);
        this.itinerary = itinerary;
    }
}
