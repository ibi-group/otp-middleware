package org.opentripplanner.middleware.connecteddataplatform;

import java.util.List;

/**
 * Helper class to combine a trip request and related trip summaries for JSON serialization.
 */
public class AnonymizedTrip {
    AnonymizedTripRequest tripRequest;
    List<AnonymizedTripSummary> tripSummaries;

    public AnonymizedTrip(AnonymizedTripRequest tripRequest, List<AnonymizedTripSummary> tripSummaries) {
        this.tripRequest = tripRequest;
        this.tripSummaries = tripSummaries;
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTrip() {
    }

    public AnonymizedTripRequest getTripRequest() {
        return tripRequest;
    }

    public List<AnonymizedTripSummary> getTripSummaries() {
        return tripSummaries;
    }
}
