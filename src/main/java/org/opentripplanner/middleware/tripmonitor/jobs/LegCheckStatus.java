package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.otp.response.Itinerary;

/**
 * Class that holds leg check status including leg match and delays.
 */
public class LegCheckStatus {
    public final boolean legsMatch;

    public final int departureDelaySeconds;

    public final int arrivalDelaySeconds;

    public final Itinerary rebuiltItinerary;

    public LegCheckStatus(
        boolean legsMatch,
        int departureDelaySeconds,
        int arrivalDelaySeconds,
        Itinerary rebuiltItinerary
    ) {
        this.legsMatch = legsMatch;
        this.departureDelaySeconds = departureDelaySeconds;
        this.arrivalDelaySeconds = arrivalDelaySeconds;
        this.rebuiltItinerary = rebuiltItinerary;
    }
}
