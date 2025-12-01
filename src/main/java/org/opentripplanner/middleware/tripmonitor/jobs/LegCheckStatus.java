package org.opentripplanner.middleware.tripmonitor.jobs;

/**
 * Class that holds leg check status including leg match and delays.
 */
public class LegCheckStatus {
    public final boolean legsMatch;

    public final int departureDelaySeconds;

    public final int arrivalDelaySeconds;

    public LegCheckStatus(boolean legMatch, int departureDelaySeconds, int arrivalDelaySeconds) {
        this.legsMatch = legMatch;
        this.departureDelaySeconds = departureDelaySeconds;
        this.arrivalDelaySeconds = arrivalDelaySeconds;
    }
}
