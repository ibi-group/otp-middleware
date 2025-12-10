package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.List;

import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;

/**
 * Class that holds leg check status including leg match and delays.
 */
public class ItineraryCheckStatus {
    public final boolean legsMatch;

    public final int departureDelaySeconds;

    public final int arrivalDelaySeconds;

    public final Itinerary rebuiltItinerary;

    public final Exception exception;

    public final boolean impossibleTransfer;

    public ItineraryCheckStatus(
        boolean legsMatch,
        Itinerary rebuiltItinerary,
        Exception exception,
        boolean impossibleTransfer
    ) {
        this.legsMatch = legsMatch;
        this.rebuiltItinerary = rebuiltItinerary;
        this.exception = exception;
        this.impossibleTransfer = impossibleTransfer;

        List<Leg> transitLegs = getTransitLegs(rebuiltItinerary.legs);
        this.departureDelaySeconds = transitLegs.get(0).departureDelay;
        this.arrivalDelaySeconds = transitLegs.get(transitLegs.size() - 1).arrivalDelay;
    }
}
