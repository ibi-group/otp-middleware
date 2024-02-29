package org.opentripplanner.middleware.triptracker;

import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class ManageLegTraversal {

    /** The smallest permitted time in seconds for a segment. */
    public static final int TRIP_TRACKING_MINIMUM_SEGMENT_TIME
        = getConfigPropertyAsInt("TRIP_TRACKING_MINIMUM_SEGMENT_TIME", 5);

    private ManageLegTraversal() {
    }

    /**
     * Define the segment within a leg that is the closest to the traveler's current position.
     */
    public static ManageLegTraversal.Segment getLegSegmentNearestToCurrentPosition(Leg leg, Coordinates currentCoordinates) {
        if (!canUseLeg(leg)) {
            return null;
        }
        double shortestDistance = Double.MAX_VALUE;
        ManageLegTraversal.Segment nearestLegSegment = null;
        List<ManageLegTraversal.Segment> segments = interpolatePoints(leg);
        for (ManageLegTraversal.Segment segment : segments) {
            double distance = getDistance(currentCoordinates, segment.start);
            if (distance < shortestDistance) {
                nearestLegSegment = segment;
                shortestDistance = distance;
            }
        }
        return nearestLegSegment;
    }

    /**
     * Get the expected traveler position using the current time and trip itinerary.
     *
     * @param currentTime Traveler's current time.
     * @param itinerary Trip itinerary.
     * @return The expected traveler coordinates.
     */
    public static ManageLegTraversal.Segment getExpectedPosition(Instant currentTime, Itinerary itinerary) {
        var expectedLeg = getExpectedLeg(currentTime, itinerary);
        return (canUseLeg(expectedLeg)) ? getExpectedPosition(currentTime, expectedLeg) : null;
    }

    /**
     * Get the expected traveler position using the current time and trip leg.
     *
     * @param currentTime Traveler's current time.
     * @param leg Trip leg.
     * @return The expected traveler coordinates.
     */
    private static ManageLegTraversal.Segment getExpectedPosition(Instant currentTime, Leg leg) {
        List<ManageLegTraversal.Segment> segments = interpolatePoints(leg);
        return getSegmentPosition(leg.startTime.toInstant(), currentTime, segments);
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
     * Using the duration of a leg and it's points, produce a list of segments each containing a representative
     * coordinate and time spent in the segment.
     */
    public static List<Segment> interpolatePoints(Leg expectedLeg) {
        List<Position> positions = PolylineUtils.decode(expectedLeg.legGeometry.points, 5);
        double totalDistance = getDistanceTraversedForLeg(positions);
        return (totalDistance > 0)
            ? getTimeInSegments(positions, expectedLeg.duration / totalDistance, expectedLeg.mode)
            : new ArrayList<>();
    }

    /**
     * Working through each segment from the beginning of the leg to the end, return the coordinates of the segment
     * which contain the current time.
     */
    public static Segment getSegmentPosition(
        Instant segmentStartTime,
        Instant currentTime,
        List<Segment> segments
    ) {
        for (Segment segment : segments) {
            // Offset the end time by a faction to avoid exact times being attributed to the wrong previous segment.
            Instant segmentEndTime = segmentStartTime.plus(
                (getSecondsToMilliseconds(segment.timeInSegment) - 1),
                ChronoUnit.MILLIS
            );
            if (isTimeInRange(segmentStartTime, segmentEndTime, currentTime)) {
                return segment;
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
    public static List<Segment> getTimeInSegments(List<Position> positions, double timePerMeter, String mode) {
        List<Segment> segments = new ArrayList<>();
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
                    segments.add(new Segment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                }
            } else {
                if (groupCoordinates != null) {
                    // Group segment is now big enough.
                    groupSegmentTime += timeInSegment;
                    segments.add(new Segment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                } else {
                    segments.add(new Segment(c1, c2, timeInSegment, mode, cumulativeTime));
                }
            }
        }
        if (groupCoordinates != null) {
            // Close incomplete group segment. This is unlikely to meet the minimum segment time.
            segments.add(new Segment(groupCoordinates, c2, groupSegmentTime, mode, cumulativeTime));
        }
        return segments;
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

    public static class Segment {

        /** The coordinates associated with the start of a segment. */
        public Coordinates start;

        /** The coordinates associated with the end of a segment. */
        public Coordinates end;

        /** The time spent in this segment in seconds. */
        public double timeInSegment;

        /** The leg mode associated with this segment. */
        public String mode;

        /** The cumulative time since the start of the leg. */
        public double cumulativeTime;

        public Segment(Coordinates start, Coordinates end, double timeInSegment, String mode, double cumulativeTime) {
            this.start = start;
            this.end = end;
            this.timeInSegment = timeInSegment;
            this.mode = mode;
            this.cumulativeTime = cumulativeTime;
        }
    }
}