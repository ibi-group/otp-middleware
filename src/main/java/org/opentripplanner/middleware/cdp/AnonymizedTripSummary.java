package org.opentripplanner.middleware.cdp;

import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.List;

/**
 * Class to hold anonymized trip summaries only.
 */
public class AnonymizedTripSummary {
    public List<Itinerary> itineraries;

    public AnonymizedTripSummary(List<Itinerary> itineraries) {
        this.itineraries = itineraries;
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripSummary() {
    }

}
