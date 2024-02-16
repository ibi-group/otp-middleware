package org.opentripplanner.middleware.triptracker;

import com.grum.geocalc.Coordinate;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;
import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ManageLegTraversal {

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
            double distance = getDistance(currentCoordinates, segment.coordinates);
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
     * Get the expected leg by comparing the current time against the start and end time of each leg. If the current time
     * is between the start and end time of a leg, this is the leg we expect the traveler to be on.
     */
    public static Leg getExpectedLeg(Instant timeNow, Itinerary itinerary) {
        if (canUseTripLegs(itinerary)) {
            for (Leg leg : itinerary.legs) {
                if (leg.startTime != null && leg.endTime != null && (isTimeInRange(
                        leg.startTime.toInstant(),
                        // Offset the end time by a faction to avoid exact times being attributed to the wrong leg.
                        leg.endTime.toInstant().minus(1, ChronoUnit.MILLIS),
                        timeNow)
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
        SortedSet<Position> orderedPoints = orderPoints(PolylineUtils.decode(expectedLeg.legGeometry.points, 5));
        double totalDistance = getDistanceTraversedForLeg(orderedPoints);
        return (totalDistance > 0)
            ? getTimeInSegments(orderedPoints, expectedLeg.duration / totalDistance, expectedLeg.mode)
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
     * Get the distance between two lat/lon points in meters.
     */
    public static double getDistance(Coordinates start, Coordinates end) {
        return EarthCalc.haversine.distance(
            Point.at(Coordinate.fromDegrees(start.lat), Coordinate.fromDegrees(start.lon)),
            Point.at(Coordinate.fromDegrees(end.lat), Coordinate.fromDegrees(end.lon))
        );
    }

    /**
     * Calculate the distance between each position and from this the number of seconds spent in each segment. If the
     * time spent in a segment is less than five seconds, group these segments. This assumes that the leg is being
     * traversed at a constant speed for simplicity.
     *
     * @param orderedPositions Points along a leg.
     * @param timePerMeter  The average time to cover a meter within a leg.
     * @return A list of segments, around five seconds in size with an associated lat/lon.
     */
    public static List<Segment> getTimeInSegments(SortedSet<Position> orderedPositions, double timePerMeter, String mode) {
        int minimumSegmentTime = 5;
        List<Segment> segments = new ArrayList<>();
        List<Position> positions = new ArrayList<>(orderedPositions);
        Coordinates groupCoordinates = null;
        double groupSegmentTime = 0;
        double cumulativeTime = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            Coordinates c1 = new Coordinates(positions.get(i).getLatitude(), positions.get(i).getLongitude());
            Coordinates c2 = new Coordinates(positions.get(i + 1).getLatitude(), positions.get(i + 1).getLongitude());
            double distance = getDistance(c1, c2);
            double timeInSegment = distance * timePerMeter;
            cumulativeTime += timeInSegment;
            if (timeInSegment < minimumSegmentTime) {
                // Time in segment too small.
                if (groupCoordinates == null) {
                    groupCoordinates = c1;
                    groupSegmentTime = 0;
                }
                groupSegmentTime += timeInSegment;
                if (groupSegmentTime > minimumSegmentTime) {
                    // Group segment is now big enough.
                    segments.add(new Segment(groupCoordinates, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                }
            } else {
                if (groupCoordinates != null) {
                    // Group segment is now big enough.
                    groupSegmentTime += timeInSegment;
                    segments.add(new Segment(groupCoordinates, groupSegmentTime, mode, cumulativeTime));
                    groupCoordinates = null;
                } else {
                    segments.add(new Segment(c1, timeInSegment, mode, cumulativeTime));
                }
            }
        }
        if (groupCoordinates != null) {
            // Close incomplete group segment.
            segments.add(new Segment(groupCoordinates, groupSegmentTime, mode, cumulativeTime));
        }
        return segments;
    }

    /**
     * Get the total distance traversed through each position on a leg. This distance is different to that provided
     * with the leg (distance).
     */
    public static double getDistanceTraversedForLeg(SortedSet<Position> orderedPositions) {
        List<Position> positions = new ArrayList<>(orderedPositions);
        double total = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            total += getDistance(
                new Coordinates(positions.get(i).getLatitude(), positions.get(i).getLongitude()),
                new Coordinates(positions.get(i + 1).getLatitude(), positions.get(i + 1).getLongitude())
            );
        }
        return total;
    }

    /**
     * Order points and remove duplicates to match leg traversal.
     */
    public static SortedSet<Position> orderPoints(List<Position> positions) {
        SortedSet<Position> sortedPoints = new TreeSet<>(
            Comparator.comparing(Position::getLatitude).thenComparing(Position::getLongitude)
        );
        sortedPoints.addAll(positions);
        return sortedPoints;
    }

    public static class Segment {

        /** The coordinates associated with this segment. */
        public Coordinates coordinates;

        /** The time spent in this segment in seconds. */
        public double timeInSegment;

        /** The leg mode associated with this segment. */
        public String mode;

        /** The cumulative time since the start of the leg. */
        public double cumulativeTime;

        public Segment(Coordinates coordinates, double timeInSegment, String mode, double cumulativeTime) {
            this.coordinates = coordinates;
            this.timeInSegment = timeInSegment;
            this.mode = mode;
            this.cumulativeTime = cumulativeTime;
        }
    }
}
