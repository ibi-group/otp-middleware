package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.util.Collection;
import java.util.List;

public class ItineraryUtils {
    private static final Collection<String> OTP_TRANSIT_MODES = List.of("TRAM", "BUS", "SUBWAY", "FERRY", "RAIL", "GONDOLA");

    /**
     * @return true if at least one of the legs of the specified itinerary is
     *   a transit leg, and none of the legs is a rental leg (e.g. CAR_RENT, BICYCLE_RENT, etc.).
     */
    public static boolean itineraryHasTransitAndNoRentals(Itinerary itinerary) {
        return itineraryHasTransit(itinerary) && !itineraryHasRental(itinerary);
    }

    /**
     * @return true if at least one {@link Leg} of the specified {@link Itinerary} is a transit leg.
     */
    public static boolean itineraryHasTransit(Itinerary itinerary) {
        if (itinerary != null && itinerary.legs != null) {
            for (Leg leg : itinerary.legs) {
                if (isTransit(leg.mode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return true if at least one of the legs of the specified itinerary is a rental leg
     *   (e.g. CAR_RENT, BICYCLE_RENT, MICROMOBILITY_RENT).
     */
    public static boolean itineraryHasRental(Itinerary itinerary) {
        if (itinerary != null && itinerary.legs != null) {
            for (Leg leg : itinerary.legs) {
                if (leg.mode.contains("_RENT")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return true if the specified mode is a transit mode
     */
    public static boolean isTransit(String mode) {
        return OTP_TRANSIT_MODES.contains(mode) || "TRANSIT".equals(mode);
    }
}
