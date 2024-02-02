package org.opentripplanner.middleware.triptracker;

import com.grum.geocalc.Coordinate;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;
import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ManageLegTraversal {

    private ManageLegTraversal() {
    }

    /**
     * Using the duration of a leg and it's points, produce a list of segments each containing a representative
     * coordinate and time spent in the segment.
     *
     * TODO: This is very repetitive, consider saving with tracked journey? Would the DB I/O take longer?!
     */
    public static List<Segment> interpolatePoints(Leg expectedLeg) {
        List<Position> positionsInLeg = removeDuplicatePoints(PolylineUtils.decode(expectedLeg.legGeometry.points, 5));
        SortedSet<Position> orderedPoints = orderPoints(positionsInLeg);
        double totalDistance = getDistanceTraversedForLeg(orderedPoints);
        List<Segment> segments = new ArrayList<>();
        if (totalDistance > 0) {
            double durationPerMeter = expectedLeg.duration / totalDistance;
            segments = getTimeInSegments(orderedPoints, durationPerMeter);
        }
        return segments;
    }

    /**
     * Working through each segment from the beginning of the leg to the end, return the coordinates of the segment
     * which contain the current time.
     */
    public static Coordinates getSegmentPosition(
        ZonedDateTime segmentStartTime,
        ZonedDateTime currentTime,
        List<Segment> segments
    ) {
        for (Segment segment : segments) {
            // Offset the end time by a faction to avoid exact times being attributed to the wrong previous segment.
            ZonedDateTime segmentEndTime = segmentStartTime.plus(
                (getTimeInMilliseconds(segment.timeInSegment)-1),
                ChronoUnit.MILLIS
            );
            if (isTimeInRange(segmentStartTime, segmentEndTime, currentTime)) {
                return segment.coordinates;
            }
            segmentStartTime = segmentEndTime;
        }
        return null;
    }

    /**
     * Convert the time in seconds to milliseconds.
     */
    public static long getTimeInMilliseconds(double timeInSeconds) {
        return (long) (timeInSeconds * 1000);
    }

    /**
     * Check if the current time is between the start and end times.
     */
    public static boolean isTimeInRange(ZonedDateTime startTime, ZonedDateTime endTime, ZonedDateTime currentTime) {
        return (currentTime.isAfter(startTime) || currentTime.isEqual(startTime)) &&
            (currentTime.isBefore(endTime) || currentTime.isEqual(endTime));
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
     * TODO: Need to include speed!
     */
    public static List<Segment> getTimeInSegments(SortedSet<Position> orderedPositions, double durationPerMeter) {
        int minimumSegmentTime = 5;
        List<Segment> segments = new ArrayList<>();
        List<Position> positions = new ArrayList<>(orderedPositions);
        Coordinates groupCoordinates = null;
        double groupSegmentTime = 0;
        for (int i=0; i < positions.size()-1; i++) {
            Coordinates c1 = new Coordinates(positions.get(i).getLatitude(), positions.get(i).getLongitude());
            Coordinates c2 = new Coordinates(positions.get(i+1).getLatitude(), positions.get(i+1).getLongitude());
            double timeInSegment = getDistance(c1, c2) * durationPerMeter;
            if (timeInSegment < minimumSegmentTime) {
                // Time in segment too small.
                if (groupCoordinates == null) {
                    groupCoordinates = c1;
                    groupSegmentTime = 0;
                }
                groupSegmentTime += timeInSegment;
                if (groupSegmentTime > minimumSegmentTime) {
                    // Group segment is now big enough.
                    segments.add(new Segment(groupCoordinates, groupSegmentTime));
                    groupCoordinates = null;
                }
            } else {
                if (groupCoordinates != null) {
                    // Group segment is now big enough.
                    groupSegmentTime += timeInSegment;
                    segments.add(new Segment(groupCoordinates, groupSegmentTime));
                    groupCoordinates = null;
                } else {
                    segments.add(new Segment(c1, timeInSegment));
                }
            }
        }
        if (groupCoordinates != null) {
            // Close incomplete group segment.
            segments.add(new Segment(groupCoordinates, groupSegmentTime));
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
        for (int i=0; i < positions.size()-1; i++) {
            double dist = getDistance(
                new Coordinates(positions.get(i).getLatitude(), positions.get(i).getLongitude()),
                new Coordinates(positions.get(i+1).getLatitude(), positions.get(i+1).getLongitude())
            );
            total += dist;
        }
        return total;
    }

    /**
     * Remove all duplicate points.
     */
    public static List<Position> removeDuplicatePoints(List<Position> positions) {
        return new ArrayList<>(new HashSet<>(positions));
    }

    /**
     * Order points to match leg traversal.
     */
    public static SortedSet<Position> orderPoints(List<Position> positions) {
        SortedSet<Position> sortedPoints = new TreeSet<>(
            Comparator.comparing(Position::getLatitude).thenComparing(Position::getLongitude)
        );
        sortedPoints.addAll(positions);
        return sortedPoints;
    }

    public static class Segment {

        public Segment(Coordinates coordinates, double timeInSegment) {
            this.coordinates = coordinates;
            this.timeInSegment = timeInSegment;
        }

        /** The coordinates associated with this segment. **/
        public Coordinates coordinates;

        /** The time spent in this segment in seconds. **/
        public double timeInSegment;
    }
}
