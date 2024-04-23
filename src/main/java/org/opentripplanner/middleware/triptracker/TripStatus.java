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
    DEVIATED,

    /**
     * Unable to ascertain the traveler's position.
     **/
    NO_STATUS;

    public static final int TRIP_TRACKING_WALK_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_WALK_ON_TRACK_RADIUS", 5);

    public static final int TRIP_TRACKING_WALK_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_WALK_DEVIATED_RADIUS", 10);

    public static final int TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS", 10);

    public static final int TRIP_TRACKING_BICYCLE_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BICYCLE_DEVIATED_RADIUS", 20);

    public static final int TRIP_TRACKING_BUS_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BUS_ON_TRACK_RADIUS", 20);

    public static final int TRIP_TRACKING_BUS_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_BUS_DEVIATED_RADIUS", 30);

    public static final int TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS", 100);

    public static final int TRIP_TRACKING_SUBWAY_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_SUBWAY_DEVIATED_RADIUS", 200);

    public static final int TRIP_TRACKING_TRAM_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_TRAM_ON_TRACK_RADIUS", 100);

    public static final int TRIP_TRACKING_TRAM_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_TRAM_DEVIATED_RADIUS", 200);

    public static final int TRIP_TRACKING_RAIL_ON_TRACK_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_RAIL_ON_TRACK_RADIUS", 200);

    public static final int TRIP_TRACKING_RAIL_DEVIATED_RADIUS
        = getConfigPropertyAsInt("TRIP_TRACKING_RAIL_DEVIATED_RADIUS", 400);

    /**
     * Define the trip status based on the traveler's current position compared to expected and nearest points on the trip.
     */
    public static TripStatus getTripStatus(TravelerPosition travelerPosition) {
        if (travelerPosition.expectedLeg != null &&
            travelerPosition.legSegmentFromPosition != null &&
            isWithinModeRadius(travelerPosition)
        ) {
            Instant segmentStartTime = travelerPosition
                .expectedLeg
                .startTime
                .toInstant()
                .plusSeconds((long) getSegmentStartTime(travelerPosition.legSegmentFromPosition));
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
        if (isWithinModeDeviatedRadius(travelerPosition)) {
            return TripStatus.DEVIATED;
        }
        return TripStatus.NO_STATUS;
    }

    public static double getSegmentStartTime(LegSegment legSegmentFromPosition) {
        return legSegmentFromPosition.cumulativeTime - legSegmentFromPosition.timeInSegment;
    }

    /**
     * Checks if the traveler's position is with an acceptable deviated distance of the mode type.
     */
    private static boolean isWithinModeDeviatedRadius(TravelerPosition travelerPosition) {
        if (travelerPosition.nearestLegSegment != null) {
            double modeBoundary = getModeRadius(travelerPosition.nearestLegSegment.mode, false);
            return travelerPosition.nearestLegSegment.distance <= modeBoundary;
        }
        return false;
    }

    /**
     * Checks if the traveler's position is with an acceptable distance of the mode type.
     */
    private static boolean isWithinModeRadius(TravelerPosition travelerPosition) {
        double distanceFromExpected = getDistanceFromLine(
            travelerPosition.legSegmentFromPosition.start,
            travelerPosition.legSegmentFromPosition.end,
            travelerPosition.currentPosition
        );
        double modeBoundary = getModeRadius(travelerPosition.legSegmentFromPosition.mode, true);
        return distanceFromExpected <= modeBoundary;
    }

    /**
     * Get the acceptable 'on track' or 'deviated' radius in meters for mode.
     */
    public static double getModeRadius(String mode, boolean onTrack) {
        switch (mode.toUpperCase()) {
            case "WALK":
                return (onTrack) ? TRIP_TRACKING_WALK_ON_TRACK_RADIUS : TRIP_TRACKING_WALK_DEVIATED_RADIUS;
            case "BICYCLE":
                return (onTrack) ? TRIP_TRACKING_BICYCLE_ON_TRACK_RADIUS : TRIP_TRACKING_BICYCLE_DEVIATED_RADIUS;
            case "BUS":
                return (onTrack) ? TRIP_TRACKING_BUS_ON_TRACK_RADIUS : TRIP_TRACKING_BUS_DEVIATED_RADIUS;
            case "SUBWAY":
                return (onTrack) ? TRIP_TRACKING_SUBWAY_ON_TRACK_RADIUS : TRIP_TRACKING_SUBWAY_DEVIATED_RADIUS;
            case "TRAM":
                return (onTrack) ? TRIP_TRACKING_TRAM_ON_TRACK_RADIUS : TRIP_TRACKING_TRAM_DEVIATED_RADIUS;
            case "RAIL":
                return (onTrack) ? TRIP_TRACKING_RAIL_ON_TRACK_RADIUS : TRIP_TRACKING_RAIL_DEVIATED_RADIUS;
            default:
                throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }
}
