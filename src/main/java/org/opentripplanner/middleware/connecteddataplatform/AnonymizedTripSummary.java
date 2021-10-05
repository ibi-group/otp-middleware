package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.utils.Coordinates;

/**
 * Class to hold anonymized trip summaries only.
 */
public class AnonymizedTripSummary {

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    public AnonymizedTripPlan tripPlan;

    public AnonymizedTripSummary(
        TripSummary tripSummary,
        Coordinates fromCoordinates,
        Coordinates toCoordinates
    ) {
        this.batchId = tripSummary.batchId;
        this.tripPlan = new AnonymizedTripPlan(
            tripSummary.date,
            tripSummary.itineraries,
            fromCoordinates,
            toCoordinates
        );
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripSummary() {
    }

}
