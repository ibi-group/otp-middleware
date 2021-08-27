package org.opentripplanner.middleware.cdp;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;

import java.util.List;

/**
 * Helper class to combine trip requests and trip summaries for JSON serialization.
 */
public class TripHistory {
    List<TripRequest> tripRequests;
    List<TripSummary> tripSummaries;

    public TripHistory(List<TripRequest> tripRequests, List<TripSummary> tripSummaries) {
        this.tripRequests = tripRequests;
        this.tripSummaries = tripSummaries;
    }
}
