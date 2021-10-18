package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.utils.Coordinates;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.models.TripSummary} containing only parameters
 * that don't contain precise user or location data.
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
            tripSummary,
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
