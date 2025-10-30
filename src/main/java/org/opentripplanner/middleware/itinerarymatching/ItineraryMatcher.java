package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

/**
 * Helper class for matching itineraries and legs.
 */
public class ItineraryMatcher {

    private final Itinerary referenceItinerary;
    private final Itinerary candidateItinerary;
    private String failingReason;

    /**
     * Creates a new instance with two itineraries.
     *
     * @param referenceItinerary The reference itinerary that others are compared against.
     * @param candidateItinerary A new itinerary that might match the previous itinerary.
     */
    public ItineraryMatcher(Itinerary referenceItinerary, Itinerary candidateItinerary) {
        this.referenceItinerary = referenceItinerary;
        this.candidateItinerary = candidateItinerary;
    }

    /**
     * Returns true if the itineraries match for the purposes of trip monitoring.
     */
    public boolean match() {
        // Make sure both itineraries are monitorable before continuing.
        if (!referenceItinerary.canBeMonitored() || !candidateItinerary.canBeMonitored()) {
            failingReason = "Reference or candidate itinerary cannot be monitored (might have a rental vehicle).";
            return false;
        }

        // make sure itineraries have same amount of legs
        if (referenceItinerary.legs.size() != candidateItinerary.legs.size()) {
            failingReason = "Reference and candidate itineraries have different numbers of legs.";
            return false;
        }

        // make sure each leg matches
        for (int i = 0; i < referenceItinerary.legs.size(); i++) {
            Leg referenceItineraryLeg = referenceItinerary.legs.get(i);
            Leg candidateItineraryLeg = candidateItinerary.legs.get(i);
            LegMatcher legMatcher = new LegMatcher(referenceItineraryLeg, candidateItineraryLeg);

            if (!legMatcher.match()) {
                failingReason = String.format("Leg %d: %s", i, legMatcher.getFailingReason());
                return false;
            }
        }

        // if this point is reached, the itineraries are assumed to match
        return true;
    }

    public String getFailingReason() {
        return failingReason;
    }
}
