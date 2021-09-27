package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.Date;
import java.util.List;

/**
 * Class to hold anonymized trip summaries only.
 */
public class AnonymizedTripSummary {

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    public AnonymizedTripPlan tripPlan;

    public AnonymizedTripSummary(String batchId, Date date, List<Itinerary> itineraries) {
        this.batchId = batchId;
        this.tripPlan = new AnonymizedTripPlan(date, itineraries);
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripSummary() {
    }

}
