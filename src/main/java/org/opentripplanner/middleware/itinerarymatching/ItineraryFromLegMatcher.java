package org.opentripplanner.middleware.itinerarymatching;

import org.apache.commons.lang3.NotImplementedException;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class that matches a collection of transit legs to a reference itinerary.
 */
public class ItineraryFromLegMatcher {
    // TODO: Make these config params.
    private static final int TRANSFER_SLACK_SECONDS = 0;
    private static final int BOARDING_SLACK_SECONDS = 0;
    private static final int ALIGHT_SLACK_SECONDS = 0;
    private static final int TOTAL_SLACK_SECONDS = ALIGHT_SLACK_SECONDS + BOARDING_SLACK_SECONDS + TRANSFER_SLACK_SECONDS;

    private final Itinerary referenceItinerary;
    private final Collection<Leg> legs;
    private Itinerary rebuiltItinerary;

    public ItineraryFromLegMatcher(Itinerary referenceItinerary, Collection<Leg> legs) {
        this.referenceItinerary = referenceItinerary;
        this.legs = legs;
    }

    public static List<Leg> getTransitLegs(Collection<Leg> legs) {
        return legs.stream()
            .filter(Leg::transitLegWithId)
            .collect(Collectors.toList());
    }

    private static List<String> getLegIds(Collection<Leg> legs) {
        return legs.stream()
            .map(leg -> leg.id)
            .collect(Collectors.toList());
    }

    public boolean match() {
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
            .collect(Collectors.toMap( leg -> leg.id, Function.identity()));

        Leg previousTransitLeg = null;
        List<Leg> transferLegs = new ArrayList<>();
        for (Leg leg : referenceItinerary.legs) {
            if (leg.transitLegWithId()) {
                if (previousTransitLeg != null) {
                    boolean transferImpossible = isInsufficientTime(
                        transitLegsById.get(previousTransitLeg.id),
                        transitLegsById.get(leg.id),
                        transferLegs
                    );
                    if (transferImpossible) return false;
                }

                previousTransitLeg = leg;
                transferLegs = new ArrayList<>();
            } else {
                transferLegs.add(leg);
            }
        }

        return true;
    }

    /**
     * Determines whether there is enough time, including slacks, for legs between the two given legs.
     */
    private static boolean isInsufficientTime(Leg fromTransitLeg, Leg toTransitleg, List<Leg> legsBetween) {
        Duration interval = Duration.between(
            fromTransitLeg.endTime.toInstant(),
            toTransitleg.startTime.toInstant()
        );

        double transferDurationSeconds = legsBetween.isEmpty()
            ? 0
            : Duration.between(
                legsBetween.get(0).startTime.toInstant(),
                legsBetween.get(legsBetween.size() - 1).endTime.toInstant()
            ).toSeconds();

        return interval.toSeconds() < transferDurationSeconds + TOTAL_SLACK_SECONDS;
    }

    /**
     * Gets an itinerary based on the original one with the updated transit legs.
     * Note: The resulting itinerary might be bogus (e.g. some legs might overlap in time).
     */
    public Itinerary getRebuiltItinerary() {
        if (rebuiltItinerary == null) {
            try {
                rebuiltItinerary = rebuildItinerary();
            } catch (CloneNotSupportedException e) {
                throw new NotImplementedException(e);
            }
        }
        return rebuiltItinerary;
    }

    private Itinerary rebuildItinerary() throws CloneNotSupportedException {
        Itinerary result = referenceItinerary.clone();

        // Interval between two consecutive transit legs should be enough for the duration
        // of all walk legs plus the boarding slack, or the transfer slack.
        List<Leg> candidateTransitLegs = getTransitLegs(legs);
        Map<String, Leg> transitLegsById = candidateTransitLegs
            .stream()
            .collect(Collectors.toMap( leg -> leg.id, Function.identity()));

        // Replace transit legs that have an id with the updated ones.
        Leg previousTransitLeg = null;
        List<Leg> transferLegs = new ArrayList<>();
        for (int i = 0; i < result.legs.size(); i++) {
            Leg leg = result.legs.get(i);
            if (leg.transitLegWithId()) {
                Leg newLeg = transitLegsById.get(leg.id);
                if (newLeg != null) {
                    result.legs.set(i, newLeg);
                }

                previousTransitLeg = leg;
                transferLegs = new ArrayList<>();
            } else {
                transferLegs.add(leg);
            }
        }

        return result;
    }
}
