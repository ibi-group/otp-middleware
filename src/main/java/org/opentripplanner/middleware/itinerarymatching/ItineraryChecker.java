package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;

/**
 * Helper class for performing an itinerary existence check.
 */
public class ItineraryChecker {

    private final Itinerary itinerary;

    private final LegFinder legFinder;

    private final LocalDate targetDate;

    public ItineraryChecker(Itinerary itinerary, LegFinder legFinder, LocalDate targetDate) {
        this.itinerary = itinerary;
        this.legFinder = legFinder;
        this.targetDate = targetDate;
    }
    /**
     * Check leg existence and returns status and delay information
     */
    public ItineraryCheckStatus checkLegs() {
        List<Leg> transitLegs = getTransitLegs(itinerary.legs);
        List<Leg> queriedLegs = new ArrayList<>();
        Map<String, String> legIdMap = new HashMap<>();
        for (Leg leg : transitLegs) {
            Leg returnedLeg = legFinder.queryLeg(leg, targetDate);
            if (returnedLeg == null) {
                break;
            } else {
                queriedLegs.add(returnedLeg);
                legIdMap.put(leg.id, returnedLeg.id);
            }
        }

        return new ItineraryFromLegMatcher(itinerary, queriedLegs, legIdMap).process();
    }
}
