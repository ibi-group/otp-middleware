package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getDistance;


/**
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripStatus {

    /** Within the expect position boundary. */
    ON_SCHEDULE,

    /** Traveler's position is behind expected. */
    BEHIND_SCHEDULE,

    /** Traveler's position is ahead of expected. */
    AHEAD_OF_SCHEDULE,

    /** The traveler has completed their trip. **/
    ENDED,

    /** The traveler has deviated from the trip route. **/
    DEVIATED,

    /** Unable to ascertain the traveler's position. **/
    NO_STATUS;

    // More status types will be added.

    /**
     * Define the trip status based on the traveler's current position compared to expected and nearest points on the trip.
     */
    public static TripStatus getTripStatus(
        Coordinates currentPosition,
        Instant currentTime,
        Leg expectedLeg,
        ManageLegTraversal.Segment expectedSegment,
        ManageLegTraversal.Segment nearestSegment
    ) {
        if (expectedLeg != null) {
            if (expectedSegment != null && isWithinModeBoundary(currentPosition, expectedSegment)) {
                return TripStatus.ON_SCHEDULE;
            }
            if (nearestSegment != null && isWithinModeBoundary(currentPosition, nearestSegment)) {
                Instant nearestSegmentTime = expectedLeg.startTime.toInstant().plusSeconds((long) nearestSegment.cumulativeTime);
                return currentTime.isBefore(nearestSegmentTime) ? TripStatus.BEHIND_SCHEDULE : TripStatus.AHEAD_OF_SCHEDULE;
            }
            return TripStatus.DEVIATED;
        }
        return TripStatus.NO_STATUS;
    }

    private static boolean isWithinModeBoundary(Coordinates currentPosition, ManageLegTraversal.Segment segment) {
        double distanceFromExpected = getDistance(currentPosition, segment.coordinates);
        double modeBoundary = getModeBoundary(segment.mode);
        return distanceFromExpected <= modeBoundary;
    }

    /**
     * Get the acceptable 'on track' boundary in meters for mode.
     */
    public static double getModeBoundary(String mode) {
        // TODO: Replace these arbitrary values with something more concrete.
        switch (mode.toUpperCase()) {
            case "WALK":
                return 5;
            case "BICYCLE":
                return 10;
            case "BUS":
                return 20;
            case "SUBWAY":
            case "TRAM":
                return 100;
            case "RAIL":
                return 200;
            default:
                throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }
}
