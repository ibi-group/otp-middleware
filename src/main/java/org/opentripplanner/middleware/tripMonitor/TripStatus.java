package org.opentripplanner.middleware.tripMonitor;

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
     * The next trip starts in the future.
     */
    TRIP_UPCOMING,
    /**
     * The trip is currently active at this time.
     */
    TRIP_ACTIVE
}
