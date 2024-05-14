package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.triptracker.TravelerLocator.getAllLegPositions;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.getLegGeoPoints;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;
import static org.opentripplanner.middleware.utils.GeometryUtils.isPointBetween;

public class ManageLegTraversal {

    /** The smallest permitted time in seconds for a segment. */
    public static final int TRIP_TRACKING_MINIMUM_SEGMENT_TIME
        = getConfigPropertyAsInt("TRIP_TRACKING_MINIMUM_SEGMENT_TIME", 5);

    private ManageLegTraversal() {
    }

    /**
     * Define the segment that is the closest to the traveler's current position. The assumption being that each segment
     * is a straight line.
     */
    public static LegSegment getSegmentFromPosition(Leg leg, Coordinates currentCoordinates) {
        if (!canUseLeg(leg)) {
            return null;
        }
        double shortestDistance = Double.MAX_VALUE;
        LegSegment nearestLegSegment = null;
        List<LegSegment> legSegments = interpolatePoints(leg);
        for (LegSegment legSegment : legSegments) {
            double distance = getDistanceFromLine(legSegment.start, legSegment.end, currentCoordinates);
            if (distance < shortestDistance) {
                nearestLegSegment = legSegment;
                shortestDistance = distance;
            }
        }
        return nearestLegSegment;
    }

    /**
     * Make sure that all the required leg parameters are present.
     */
    private static boolean canUseLeg(Leg leg) {
        return
            leg != null &&
            leg.duration > 0 &&
            leg.legGeometry != null &&
            leg.legGeometry.points != null &&
            !leg.legGeometry.points.isEmpty();
    }

    /**
     * Get the expected leg by first checking to see if two points on a leg contain the current position. If there is a
     * match, return this leg, if not simply return the leg that is nearest to the current position.
     */
    @Nullable
    public static Leg getExpectedLeg(Coordinates position, Itinerary itinerary) {
        if (canUseTripLegs(itinerary)) {
            Leg leg = getContainingLeg(position, itinerary);
            return (leg != null) ? leg : getNearestLeg(position, itinerary);
        }
        return null;
    }

    /**
     * Get the leg following the expected leg.
     */
    @Nullable
    public static Leg getNextLeg(Leg expectedLeg, Itinerary itinerary) {
        if (canUseTripLegs(itinerary)) {
            for (int i = 0; i < itinerary.legs.size(); i++) {
                Leg leg = itinerary.legs.get(i);
                if (leg.equals(expectedLeg) && (i + 1 < itinerary.legs.size())) {
                    return itinerary.legs.get(i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Get the leg that is nearest to the current position. Note, to be considered when working with transit legs: if
     * the trip involves traversing a cul-de-sac, the entrance and exit legs would be very close together if not
     * identical. In this scenario it would be possible for the current position to be attributed to the exit leg,
     * therefore missing the instruction at the end of the cul-de-sac.
     */
    private static Leg getNearestLeg(Coordinates position, Itinerary itinerary) {
        double shortestDistance = Double.MAX_VALUE;
        Leg nearestLeg = null;
        for (int i = 0; i < itinerary.legs.size(); i++) {
            Leg leg = itinerary.legs.get(i);
            for (Coordinates positionOnLeg : getAllLegPositions(leg)) {
                double distance = getDistance(positionOnLeg, position);
                if (distance < shortestDistance) {
                    nearestLeg = leg;
                    shortestDistance = distance;
                }
            }
        }
        return nearestLeg;
    }

    /**
     * Get the leg containing the current position.
     */
    private static Leg getContainingLeg(Coordinates position, Itinerary itinerary) {
        for (int i = 0; i < itinerary.legs.size(); i++) {
            Leg leg = itinerary.legs.get(i);
            List<Coordinates> allPositions = getAllLegPositions(leg);
            for (int j = 0; j < allPositions.size() - 1; j++) {
                if (isPointBetween(allPositions.get(j), allPositions.get(j + 1), position)) {
                    return leg;
                }
            }
        }
        return null;
    }

    /**
     * A trip must have an itinerary that contains at least one leg to qualify for tracking.
     */
    private static boolean canUseTripLegs(Itinerary itinerary) {
        return
            itinerary != null &&
            itinerary.legs != null &&
            !itinerary.legs.isEmpty();
    }

    /**
     * Using the duration of a leg and its points, produce a list of segments each containing a representative
     * coordinate and time spent in the segment.
     */
    public static List<LegSegment> interpolatePoints(Leg expectedLeg) {
        List<Coordinates> positions = getLegGeoPoints(expectedLeg);
        double totalDistance = getDistanceTraversedForLeg(positions);
        return (totalDistance > 0)
            ? getTimeInSegments(positions, expectedLeg.duration / totalDistance, expectedLeg.mode)
            : new ArrayList<>();
    }

    /**
     * Convert the time in seconds to milliseconds.
     */
    public static long getSecondsToMilliseconds(double timeInSeconds) {
        return (long) (timeInSeconds * 1000);
    }

    /**
     * Calculate the distance between each position and from this the number of seconds spent in each segment. If the
     * time spent in a segment is less than permitted, group these segments. This assumes that the leg is being
     * traversed at a constant speed for simplicity.
     *
     * @param positions Points along a leg.
     * @param timePerMeter  The average time to cover a meter within a leg.
     * @return A list of segments.
     */
    public static List<LegSegment> getTimeInSegments(List<Coordinates> positions, double timePerMeter, String mode) {
        List<LegSegment> legSegments = new ArrayList<>();
        Coordinates groupCoordinates = null;
        double groupSegmentTime = 0;
        double cumulativeTime = 0;
        Coordinates c1;
        Coordinates c2 = null;
        for (int i = 0; i < positions.size() - 1; i++) {
            c1 = positions.get(i);
            c2 = positions.get(i + 1);
            double timeInSegment = getDistance(c1, c2) * timePerMeter;
            cumulativeTime += timeInSegment;
            if (timeInSegment < TRIP_TRACKING_MINIMUM_SEGMENT_TIME) {
                // Time in segment too small.
                if (groupCoordinates == null) {
                    groupCoordinates = c1;
                    groupSegmentTime = 0;
                }
                groupSegmentTime += timeInSegment;
                if (groupSegmentTime > TRIP_TRACKING_MINIMUM_SEGMENT_TIME) {
                    // Group segment is now big enough.
                    legSegments.add(new LegSegment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                }
            } else {
                if (groupCoordinates != null) {
                    // Group segment is now big enough.
                    groupSegmentTime += timeInSegment;
                    legSegments.add(new LegSegment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                } else {
                    legSegments.add(new LegSegment(c1, c2, timeInSegment, mode, cumulativeTime));
                }
            }
        }
        if (groupCoordinates != null) {
            // Close incomplete group segment. This is unlikely to meet the minimum segment time.
            legSegments.add(new LegSegment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
        }
        return legSegments;
    }

    /**
     * Get the total distance traversed through each position on a leg. This distance is different to that provided
     * with the leg (distance).
     */
    public static double getDistanceTraversedForLeg(List<Coordinates> positions) {
        double total = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            total += getDistance(positions.get(i), positions.get(i + 1));
        }
        return total;
    }
}