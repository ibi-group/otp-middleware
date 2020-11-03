package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

/**
 * A utility class for dealing with OTP queries and itineraries.
 */
public class ItineraryUtils {
    /**
     * Determines whether the specified {@link Itinerary} can be monitored.
     * @return true if at least one {@link Leg} of the specified {@link Itinerary} is a transit leg,
     *   and none of the legs is a rental or ride hail leg (e.g. CAR_RENT, CAR_HAIL, BICYCLE_RENT, etc.).
     *   (We use the corresponding fields returned by OTP to get transit legs and rental/ride hail legs.)
     */
    public static boolean itineraryCanBeMonitored(Itinerary itinerary) {
        boolean hasTransit = false;
        boolean hasRentalOrRideHail = false;

        if (itinerary != null && itinerary.legs != null) {
            for (Leg leg : itinerary.legs) {
                if (leg.transitLeg != null && leg.transitLeg) {
                    hasTransit = true;
                }
                if (leg.rentedBike != null && leg.rentedBike ||
                    leg.rentedCar != null && leg.rentedCar ||
                    leg.rentedVehicle != null && leg.rentedVehicle ||
                    leg.hailedCar != null && leg.hailedCar) {
                   hasRentalOrRideHail = true;
                }
            }
        }

        return hasTransit && !hasRentalOrRideHail;
    }
}
