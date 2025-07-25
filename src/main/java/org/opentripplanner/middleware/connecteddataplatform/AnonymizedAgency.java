package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Agency;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Agency}.
 */
public class AnonymizedAgency {
    public String id;

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedAgency() {
    }

    public AnonymizedAgency(Agency agency) {
        this.id = agency.id;
    }
}
