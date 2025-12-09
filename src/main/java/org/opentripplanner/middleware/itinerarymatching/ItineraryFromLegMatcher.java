package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Map leg ids of a saved itinerary to leg ids applicable to the day of actual trip.
     */
    private final Map<String, String> legIdMap;

    private Itinerary rebuiltItinerary;
    private boolean rebuildAttempted;
    private Exception exception;
    private boolean legsMatch;
    private boolean impossibleTransfer;

    public ItineraryFromLegMatcher(Itinerary referenceItinerary, Collection<Leg> legs, Map<String, String> legIdMap) {
        this.referenceItinerary = referenceItinerary;
        this.legs = legs;
        this.legIdMap = legIdMap;
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
        List<Leg> itineraryTransitLegs = getTransitLegs(referenceItinerary.legs);

        Map<String, Leg> transitLegsById = getTransitLegs(legs)
            .stream()
            .collect(Collectors.toMap( leg -> leg.id, Function.identity()));

        // Check that all legs from the reference itinerary can be mapped to the provided legs.
        long mappedLegCount = itineraryTransitLegs
            .stream()
            .map(leg -> legIdMap.get(leg.id))
            .filter(Objects::nonNull)
            .distinct()
            .map(transitLegsById::get)
            .filter(Objects::nonNull)
            .count();

        return mappedLegCount == itineraryTransitLegs.size();
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
    public Itinerary getRebuiltItinerary() {
        if (rebuiltItinerary == null && !rebuildAttempted) {
            rebuildAttempted = true;
            rebuiltItinerary = rebuildItinerary();
        }
        return rebuiltItinerary;
    }

    private Itinerary rebuildItinerary() {
        if (!hasRequiredLegs()) {
            legsMatch = false;
            return null;
        }

        Itinerary result;
        try {
            result = referenceItinerary.clone();
        } catch (CloneNotSupportedException cloneEx) {
            exception = cloneEx;
            return null;
        }

        List<Leg> candidateTransitLegs = getTransitLegs(legs);
        Map<String, Leg> transitLegsById = candidateTransitLegs
            .stream()
            .collect(Collectors.toMap( leg -> leg.id, Function.identity()));

        // Replace transit legs that have an id with the updated ones.
        Leg previousTransitLeg = null;
        Leg previousOriginalTransitLeg = null;
        List<Leg> transferLegs = new ArrayList<>();
        List<Leg> resultLegs = result.legs;
        for (int i = 0; i < resultLegs.size(); i++) {
            Leg leg = resultLegs.get(i);
            if (leg.transitLegWithId()) {
                Leg newLeg = transitLegsById.get(legIdMap.get(leg.id));
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

                        // Shift times of transfer legs so that they start right after the previous transit leg,
                        // or if there was no previous transit leg, shift by the delay on the first transit leg.
                        Duration timeDiff = Duration.between(previousOriginalTransitLeg.endTime.toInstant(), previousTransitLeg.endTime.toInstant());
                        transferLegs.forEach(l -> {
                            l.startTime = Date.from(l.startTime.toInstant().plusSeconds(timeDiff.toSeconds()));
                            l.endTime = Date.from(l.endTime.toInstant().plusSeconds(timeDiff.toSeconds()));
                        });
                    } else {
                        Duration timeDiff = Duration.between(leg.startTime.toInstant(), newLeg.startTime.toInstant());
                        transferLegs.forEach(l -> {
                            l.startTime = Date.from(l.startTime.toInstant().plusSeconds(timeDiff.toSeconds()));
                            l.endTime = Date.from(l.endTime.toInstant().plusSeconds(timeDiff.toSeconds()));
                        });
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
            Duration timeDiff = Duration.between(previousOriginalTransitLeg.endTime.toInstant(), previousTransitLeg.endTime.toInstant());
            transferLegs.forEach(l -> {
                l.startTime = Date.from(l.startTime.toInstant().plusSeconds(timeDiff.toSeconds()));
                l.endTime = Date.from(l.endTime.toInstant().plusSeconds(timeDiff.toSeconds()));
            });
        }

        // Set itinerary new start and end time
        result.startTime = resultLegs.get(0).startTime;
        result.endTime = resultLegs.get(resultLegs.size() - 1).endTime;
        return result;
    }

    public boolean legsMatch() {
        return legsMatch;
    }

    public Exception exception() {
        return exception;
    }

    public boolean impossibleTransfer() {
        return impossibleTransfer;
    }
}
