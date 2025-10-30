package org.opentripplanner.middleware.itinerarymatching;

import jersey.repackaged.com.google.common.collect.Lists;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.List;

/**
 * Helper class for matching itineraries and legs.
 */
public class ItineraryMatcher {

    private final Itinerary referenceItinerary;
    private final Itinerary candidateItinerary;
    private MatcherResult result;

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
        boolean sameLegSize = referenceItinerary.legs.size() == candidateItinerary.legs.size();
        List<Match> criteria = Lists.newArrayList(
            new Match(referenceItinerary::canBeMonitored, "Reference itin cannot be monitored"),
            new Match(candidateItinerary::canBeMonitored, "Candidate itin cannot be monitored"),
            new Match(() -> sameLegSize, "Itineraries don't have the same number of legs.")
        );
        // Make sure each leg matches.
        if (sameLegSize) {
            for (int i = 0; i < referenceItinerary.legs.size(); i++) {
                LegMatcher legMatcher = new LegMatcher(referenceItinerary.legs.get(i), candidateItinerary.legs.get(i));
                int friendlyIndex = i + 1;
                criteria.add(new Match(legMatcher::match, () -> String.format("Leg %d: %s", friendlyIndex, legMatcher.getFailingReason())));
            }
        }

        result = Match.all(criteria);
        return result.isSuccessful();
    }

    public String getFailingReason() {
        return result.getFailingMatchDescription();
    }
}
