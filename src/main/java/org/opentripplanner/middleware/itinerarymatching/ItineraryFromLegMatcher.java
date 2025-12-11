package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
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
    private final List<Leg> originalTransitLegs;
    private final Map<String, Leg> originalLegIdToCandidateLeg;

    private Itinerary rebuiltItinerary;
    private boolean rebuildAttempted;
    private Exception exception;
    private boolean impossibleTransfer;

    public ItineraryFromLegMatcher(
        Itinerary referenceItinerary,
        Collection<Leg> candidateLegs,
        Map<String, String> legIdMap
    ) {
        this.referenceItinerary = referenceItinerary;
        originalTransitLegs = getTransitLegs(referenceItinerary.legs);
        originalLegIdToCandidateLeg = mapOriginalLegIdsToCandidateLegs(candidateLegs, legIdMap);
    }

    private Map<String, Leg> mapOriginalLegIdsToCandidateLegs(Collection<Leg> candidateLegs, Map<String, String> legIdMap) {
        Map<String, Leg> candidateLegsById = getTransitLegs(candidateLegs)
            .stream()
            .collect(Collectors.toMap( leg -> leg.id, Function.identity()));

        Map<String, Leg> result = new HashMap<>();
        for (Leg leg : originalTransitLegs) {
            String mappedId = legIdMap.get(leg.id);
            if (mappedId != null) {
                Leg mappedLeg = candidateLegsById.get(mappedId);
                if (mappedLeg != null) {
                    result.put(leg.id, mappedLeg);
                }
            }
        }
        return result;
    }

    public static List<Leg> getTransitLegs(Collection<Leg> legs) {
        return legs.stream()
            .filter(Leg::transitLegWithId)
            .collect(Collectors.toList());
    }

    /**
     * Determines if all required legs to reconstruct the itinerary have been provided.
     */
    public boolean hasRequiredLegs() {
        return !originalTransitLegs.isEmpty() && originalLegIdToCandidateLeg.size() == originalTransitLegs.size();
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
     * Note: The resulting itinerary might be null or bogus (e.g. some legs might overlap in time),
     * so look at the other fields for issues.
     */
    public ItineraryCheckStatus process() {
        if (rebuiltItinerary == null && !rebuildAttempted) {
            rebuildAttempted = true;
            rebuiltItinerary = rebuildItinerary();
        }

        return new ItineraryCheckStatus(
            hasRequiredLegs(),
            rebuiltItinerary,
            exception,
            impossibleTransfer
        );
    }

    private static void offsetTimes(Collection<Leg> legs, Date from, Date to) {
        Duration timeDiff = Duration.between(from.toInstant(), to.toInstant());
        legs.forEach(l -> l.offsetTimes(timeDiff.toMillis()));
    }

    private Itinerary rebuildItinerary() {
        if (!hasRequiredLegs()) {
            return null;
        }

        Itinerary result;
        try {
            result = referenceItinerary.clone();
        } catch (CloneNotSupportedException cloneEx) {
            exception = cloneEx;
            return null;
        }

        // Replace transit legs that have an id with the updated ones.
        Leg previousTransitLeg = null;
        Leg previousOriginalTransitLeg = null;
        List<Leg> transferLegs = new ArrayList<>();
        List<Leg> resultLegs = result.legs;
        for (int i = 0; i < resultLegs.size(); i++) {
            Leg leg = resultLegs.get(i);
            if (leg.transitLegWithId()) {
                Leg newLeg = originalLegIdToCandidateLeg.get(leg.id);
                if (newLeg != null) {
                    resultLegs.set(i, newLeg);
                    if (previousTransitLeg != null) {
                        // Interval between two consecutive transit legs should be enough for the duration
                        // of all walk legs plus the boarding slack, or the transfer slack.
                        boolean transferImpossible = isInsufficientTime(
                            previousTransitLeg,
                            newLeg,
                            transferLegs
                        );
                        if (transferImpossible) impossibleTransfer = true;

                        // Shift times of transfer legs so that they start right after the previous transit leg.
                        offsetTimes(transferLegs, previousOriginalTransitLeg.endTime, previousTransitLeg.endTime);
                    } else {
                        // If there was no previous transit leg, shift by the delay on the first transit leg.
                        offsetTimes(transferLegs, leg.startTime, newLeg.startTime);
                    }
                    previousTransitLeg = newLeg;
                    previousOriginalTransitLeg = leg;
                    transferLegs = new ArrayList<>();
                }
            } else {
                transferLegs.add(leg);
            }
        }

        // Shift any remaining transfer (rather: egress) legs
        if (previousTransitLeg != null) {
            offsetTimes(transferLegs, previousOriginalTransitLeg.endTime, previousTransitLeg.endTime);
        }

        // Set itinerary new start and end time
        result.startTime = resultLegs.get(0).startTime;
        result.endTime = resultLegs.get(resultLegs.size() - 1).endTime;
        return result;
    }

    public boolean processed() {
        return rebuildAttempted;
    }
}
