package org.opentripplanner.middleware.cdp;

import java.util.List;

/**
 * Helper class to combine trip requests and trip summaries for JSON serialization.
 */
public class TripHistory {
    List<AnonymizedTripRequest> tripRequests;
    List<AnonymizedTripSummary> tripSummaries;

    public TripHistory(List<AnonymizedTripRequest> tripRequests, List<AnonymizedTripSummary> tripSummaries) {
        this.tripRequests = tripRequests;
        this.tripSummaries = tripSummaries;
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public TripHistory() {
    }

    public List<AnonymizedTripRequest> getTripRequests() {
        return tripRequests;
    }

    public List<AnonymizedTripSummary> getTripSummaries() {
        return tripSummaries;
    }
}
