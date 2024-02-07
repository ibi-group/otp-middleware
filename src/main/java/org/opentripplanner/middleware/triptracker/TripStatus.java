package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getDistance;

/**
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripStatus {

    /** Within the expect position boundary. **/
    ON_TRACK,

    /** Within an acceptable distance from any point on the trip. **/
    NEAR_BY,

    /** Outside the acceptable boundary, but is the expected position. **/
    ON_ROUTE,

    /** The traveler has completed their trip. **/
    ENDED,

    /** The traveler has deviated from the trip route. **/
    DEVIATED,

    /** Unable to ascertain the traveler's position. **/
    NO_STATUS;

    // More status types will be added.

    /**
     * Define the trip status based on the traveler's distance from an expect position and trip route.
     */
    public static TripStatus getConfidence(
        Coordinates currentPosition,
        Coordinates expectedPosition,
        double modeBoundary,
        double distanceFromNearestTripPosition
    ) {
        if (expectedPosition == null || modeBoundary == Double.MIN_VALUE && distanceFromNearestTripPosition == Double.MAX_VALUE) {
            return NO_STATUS;
        }

        int confidence = 0;

        // TODO: Replace this arbitrary value with something more concrete.
        if (distanceFromNearestTripPosition <= 20) {
            confidence = 1;
        }

        double distanceFromExpected = getDistance(currentPosition, expectedPosition);
        if (distanceFromExpected == distanceFromNearestTripPosition) {
            // Both are referring to the same point on the trip. This can only be trumped by the distance from expected
            // being within the acceptable mode boundary.
            confidence = 2;
        }
        if (distanceFromExpected <= modeBoundary) {
            confidence = 3;
        }

        switch (confidence) {
            case 0:
                return DEVIATED;
            case 1:
                return NEAR_BY;
            case 2:
                return ON_ROUTE;
            case 3:
                return ON_TRACK;
            default:
                return NO_STATUS;
        }
    }
}
