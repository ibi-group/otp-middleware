package org.opentripplanner.middleware.tripmonitor.jobs;

/**
 * Class that holds leg check status including existence and delays.
 */
public class LegCheckStatus {
    public final boolean legsExist;

    public final int departureDelaySeconds;

    public final int arrivalDelaySeconds;

    public LegCheckStatus(boolean legExist, int departureDelaySeconds, int arrivalDelaySeconds) {
        this.legsExist = legExist;
        this.departureDelaySeconds = departureDelaySeconds;
        this.arrivalDelaySeconds = arrivalDelaySeconds;
    }
}
