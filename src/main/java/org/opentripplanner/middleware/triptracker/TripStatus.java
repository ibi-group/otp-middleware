package org.opentripplanner.middleware.triptracker;

import java.time.Instant;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;


/**
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripStatus {

    /**
     * Within the expect position boundary.
     */
    ON_SCHEDULE,

    /**
     * Traveler's position is behind expected.
     */
    BEHIND_SCHEDULE,

    /**
     * Traveler's position is ahead of expected.
     */
    AHEAD_OF_SCHEDULE,

    /**
     * The traveler has completed their trip.
     **/
    ENDED,

    /**
     * The traveler has deviated from the trip route.
     **/
    DEVIATED;

    public static final int TRIP_TRACKING_WALK_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_WALK_ON_TRACK_RADIUS", 5);

    public static final int TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS", 10);

    public static final int TRIP_TRACKING_BUS_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BUS_ON_TRACK_RADIUS", 20);

    public static final int TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS", 100);

    public static final int TRIP_TRACKING_TRAM_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_TRAM_ON_TRACK_RADIUS", 100);

    public static final int TRIP_TRACKING_RAIL_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_RAIL_ON_TRACK_RADIUS", 200);

    /**
     * Define the trip status based on the traveler's current position compared to expected and nearest points on the trip.
     */
    public static TripStatus getTripStatus(TravelerPosition travelerPosition) {
        if (travelerPosition.expectedLeg != null &&
            travelerPosition.legSegmentFromPosition != null &&
            isWithinModeRadius(travelerPosition)
        ) {
            Instant segmentStartTime = getSegmentStartTime(travelerPosition);
            Instant segmentEndTime = travelerPosition
                .expectedLeg
                .startTime
                .toInstant()
                .plusSeconds((long) travelerPosition.legSegmentFromPosition.cumulativeTime);
            if (travelerPosition.currentTime.isBefore(segmentStartTime)) {
                return TripStatus.AHEAD_OF_SCHEDULE;
            } else if (travelerPosition.currentTime.isAfter(segmentEndTime)) {
                return TripStatus.BEHIND_SCHEDULE;
            } else {
                return TripStatus.ON_SCHEDULE;
            }
        }
        return TripStatus.DEVIATED;
    }

    public static double getLegSegmentStartTime(LegSegment legSegmentFromPosition) {
        return legSegmentFromPosition.cumulativeTime - legSegmentFromPosition.timeInSegment;
    }

    public static Instant getSegmentStartTime(TravelerPosition travelerPosition) {
        return travelerPosition
            .expectedLeg
            .startTime
            .toInstant()
            .plusSeconds((long) getLegSegmentStartTime(travelerPosition.legSegmentFromPosition));
    }

    /**
     * Checks if the traveler's position is with an acceptable distance of the mode type.
     */
    private static boolean isWithinModeRadius(TravelerPosition travelerPosition) {
        double distanceFromExpected = travelerPosition.getDeviationMeters();
        double modeBoundary = getModeRadius(travelerPosition.legSegmentFromPosition.mode);
        return distanceFromExpected <= modeBoundary;
    }

    /**
     * Get the acceptable 'on track' radius in meters for mode.
     */
    public static double getModeRadius(String mode) {
        switch (mode.toUpperCase()) {
            case "WALK":
                return TRIP_TRACKING_WALK_ON_TRACK_RADIUS;
            case "BICYCLE":
                return TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS;
            case "BUS":
                return TRIP_TRACKING_BUS_ON_TRACK_RADIUS;
            case "SUBWAY":
                return TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS;
            case "TRAM":
                return TRIP_TRACKING_TRAM_ON_TRACK_RADIUS;
            case "RAIL":
                return TRIP_TRACKING_RAIL_ON_TRACK_RADIUS;
            default:
                throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }
}
