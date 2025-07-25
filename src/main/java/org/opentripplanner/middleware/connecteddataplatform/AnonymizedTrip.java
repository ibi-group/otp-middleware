package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Trip;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Trip}.
 */
public class AnonymizedTrip {

    public String id;
    public String blockId;

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTrip() {
    }

    public AnonymizedTrip(Trip trip) {
        this.id = trip.id;
        this.blockId = trip.blockId;
    }
}
