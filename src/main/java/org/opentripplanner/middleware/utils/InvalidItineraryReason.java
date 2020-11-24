package org.opentripplanner.middleware.utils;

/**
 * The set of reasons for which an {@link org.opentripplanner.middleware.otp.response.Itinerary} could be invalid for
 * trip monitoring.
 */
public enum InvalidItineraryReason {
    MISSING_TRANSIT("trip is missing a transit leg"),
    HAS_RENTAL_OR_RIDE_HAIL("trip contains rental or ride hail leg");

    private final String message;
    InvalidItineraryReason(String message) {
        this.message = message;
    }
    public String getMessage() {
        return message;
    }
}
