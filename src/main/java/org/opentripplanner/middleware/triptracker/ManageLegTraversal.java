package org.opentripplanner.middleware.triptracker;

import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;

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
        LegSegment nearestLegLegSegment = null;
        List<LegSegment> legSegments = interpolatePoints(leg);
        for (LegSegment legSegment : legSegments) {
            double distance = getDistanceFromLine(legSegment.start, legSegment.end, currentCoordinates);
            if (distance < shortestDistance) {
                nearestLegLegSegment = legSegment;
                shortestDistance = distance;
            }
        }
        return nearestLegLegSegment;
    }

    /**
     * Get the expected traveler position using the current time and trip itinerary.
     */
    @Nullable
    public static LegSegment getSegmentFromTime(Instant currentTime, Itinerary itinerary) {
        var expectedLeg = getExpectedLeg(currentTime, itinerary);
        return (canUseLeg(expectedLeg)) ? getSegmentFromTime(currentTime, expectedLeg) : null;
    }

    /**
     * Get the expected traveler position using the current time and trip leg.
     */
    private static LegSegment getSegmentFromTime(Instant currentTime, Leg leg) {
        List<LegSegment> legSegments = interpolatePoints(leg);
        return getSegmentFromTime(leg.startTime.toInstant(), currentTime, legSegments);
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
     * Get the expected leg by comparing the current time against the start and end time of each leg.
     */
    @Nullable
    public static Leg getExpectedLeg(Instant timeNow, Itinerary itinerary) {
        if (canUseTripLegs(itinerary)) {
            for (int i = 0; i < itinerary.legs.size(); i++) {
                Leg leg = itinerary.legs.get(i);
                if (i == 0 &&
                    leg.mode.equalsIgnoreCase("walk") &&
                    leg.startTime != null &&
                    timeNow.isBefore(leg.startTime.toInstant())) {
                    // If the first leg is a walk leg and the traveler has decided to start the trip early.
                    return leg;
                }
                if (leg.startTime != null &&
                    leg.endTime != null &&
                    isTimeInRange(
                        leg.startTime.toInstant(),
                        // Offset the end time by a faction to avoid exact times being attributed to the wrong leg.
                        leg.endTime.toInstant().minus(1, ChronoUnit.MILLIS),
                        timeNow
                    )) {
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
        List<Position> positions = PolylineUtils.decode(expectedLeg.legGeometry.points, 5);
        double totalDistance = getDistanceTraversedForLeg(positions);
        return (totalDistance > 0)
            ? getTimeInSegments(positions, expectedLeg.duration / totalDistance, expectedLeg.mode)
            : new ArrayList<>();
    }

    /**
     * Get the segment which contains the current time.
     */
    @Nullable
    public static LegSegment getSegmentFromTime(
        Instant segmentStartTime,
        Instant currentTime,
        List<LegSegment> legSegments
    ) {
        for (LegSegment legSegment : legSegments) {
            // Offset the end time by a fraction to avoid exact times being attributed to the wrong previous segment.
            Instant segmentEndTime = segmentStartTime.plus(
                (getSecondsToMilliseconds(legSegment.timeInSegment) - 1),
                ChronoUnit.MILLIS
            );
            if (isTimeInRange(segmentStartTime, segmentEndTime, currentTime)) {
                return legSegment;
            }
            segmentStartTime = segmentEndTime;
        }
        return null;
    }

    /**
     * Convert the time in seconds to milliseconds.
     */
    public static long getSecondsToMilliseconds(double timeInSeconds) {
        return (long) (timeInSeconds * 1000);
    }

    /**
     * Check if the current time is between the start and end times.
     */
    public static boolean isTimeInRange(Instant startTime, Instant endTime, Instant currentTime) {
        return (currentTime.isAfter(startTime) || currentTime.equals(startTime)) &&
            (currentTime.isBefore(endTime) || currentTime.equals(endTime));
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
    public static List<LegSegment> getTimeInSegments(List<Position> positions, double timePerMeter, String mode) {
        List<LegSegment> legSegments = new ArrayList<>();
        Coordinates groupCoordinates = null;
        double groupSegmentTime = 0;
        double cumulativeTime = 0;
        Coordinates c1;
        Coordinates c2 = null;
        for (int i = 0; i < positions.size() - 1; i++) {
            c1 = new Coordinates(positions.get(i));
            c2 = new Coordinates(positions.get(i + 1));
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
    public static double getDistanceTraversedForLeg(List<Position> orderedPositions) {
        List<Position> positions = new ArrayList<>(orderedPositions);
        double total = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            total += getDistance(
                new Coordinates(positions.get(i)),
                new Coordinates(positions.get(i + 1))
            );
        }
        return total;
    }
}