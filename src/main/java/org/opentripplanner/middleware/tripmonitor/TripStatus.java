package org.opentripplanner.middleware.tripmonitor;

import org.opentripplanner.middleware.models.MonitoredTrip;

/**
 * An enum of statuses to describe the current overall state of a {@link MonitoredTrip}
 */
public enum TripStatus {
    /**
     * The trip is no longer possible on any actively monitored day of the week.
     */
    NO_LONGER_POSSIBLE,
    /**
     * The next trip is not possible, but other active days of the week might still be possible.
     */
    NEXT_TRIP_NOT_POSSIBLE,
    /**
     * The next trip starts at some point in the future. This status will be set all the way until the itinerary for
     * this trip actually begins according to the most recently fetched itinerary from the trip planner.
     */
    TRIP_UPCOMING,
    /**
     * The trip is currently active at this time. This status will be set while the current time is after the start time
     * and before the end time of the itinerary for the trip most recently fetched from the trip planner.
     */
    TRIP_ACTIVE
}
