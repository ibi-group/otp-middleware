package org.opentripplanner.middleware.itinerarymatching;

import org.apache.logging.log4j.util.Strings;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class that matches a collection of transit legs to a reference itinerary.
 */
public class ItineraryFromLegMatcher {

    private final Itinerary referenceItinerary;

    private final Collection<Leg> legs;

    ItineraryFromLegMatcher(Itinerary referenceItinerary, Collection<Leg> legs) {
        this.referenceItinerary = referenceItinerary;
        this.legs = legs;
    }

    private static List<Leg> getTransitLegs(Collection<Leg> legs) {
        return legs.stream()
            .filter(leg -> Boolean.TRUE.equals(leg.transitLeg))
            .filter(leg -> !Strings.isBlank(leg.id))
            .collect(Collectors.toList());
    }

    private static List<String> getLegIds(Collection<Leg> legs) {
        return legs.stream()
            .map(leg -> leg.id)
            .collect(Collectors.toList());
    }

    public boolean match() {
        // TODO: Make these config params.
        int transferSlackSeconds = 0;
        int boardingSlackSeconds = 0;

        // Check that there are the same number of transit legs
        List<Leg> candidateTransitLegs = getTransitLegs(legs);
        List<Leg> itineraryTransitLegs = getTransitLegs(referenceItinerary.legs);

        // Check that ids are the same, same size and in same order
        List<String> referenceIds = getLegIds(itineraryTransitLegs);
        List<String> candidateIds = getLegIds(candidateTransitLegs);

        if (!referenceIds.equals(candidateIds)) return false;

        // Interval between two consecutive transit legs should be enough for the duration
        // of all walk legs plus the boarding slack, or the transfer slack.
        Map<String, Leg> transitLegsById = candidateTransitLegs
            .stream()
            .collect(Collectors.toMap(leg -> leg.id, leg -> leg));

        Leg previousTransitLeg = null;
        List<Leg> transferLegs = new ArrayList<>();
        double transferDurationSeconds = 0;
        boolean transferImpossible = false;
        for (Leg leg : referenceItinerary.legs) {
            if (Boolean.TRUE.equals(leg.transitLeg)) {
                if (previousTransitLeg != null) {
                    Duration interval = Duration.between(
                        transitLegsById.get(previousTransitLeg.id).endTime.toInstant(),
                        transitLegsById.get(leg.id).startTime.toInstant()
                    );

                    // If there are no transfer legs, keep a minimum transfer slack.
                    // Otherwise, use the boarding slack.
                    if (
                        (transferLegs.isEmpty() && interval.toSeconds() < transferSlackSeconds) ||
                        (interval.toSeconds() < boardingSlackSeconds + transferDurationSeconds)
                    ) {
                        transferImpossible = true;
                        break;
                    }
                }

                previousTransitLeg = leg;
                transferLegs = new ArrayList<>();
                transferDurationSeconds = 0;
            } else {
                transferLegs.add(leg);
                if (leg.duration != null) transferDurationSeconds += leg.duration;
            }
        }

        return !transferImpossible;
    }
}
