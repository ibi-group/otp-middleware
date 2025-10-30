package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.List;

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
        List<Match> criteria = List.of(
            new Match(() -> referenceItinerary.canBeMonitored(), "Reference itin cannot be monitored"),
            new Match(() -> candidateItinerary.canBeMonitored(), "Candidate itin cannot be monitored"),
            new Match(() -> referenceItinerary.legs.size() == candidateItinerary.legs.size(), "Itineraries don't have the same number of legs.")
        );

        for (Match m : criteria) {
            boolean result = m.criterion.getAsBoolean();
            if (!result) {
                failingReason = m.description;
                return false;
            }
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
