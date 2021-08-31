package org.opentripplanner.middleware.cdp;

import org.opentripplanner.middleware.models.OtpUser;

/**
 * Class to hold anonymized trip requests only.
 */
public class AnonymizedTripRequest {
    /**
     * User Id. {@link OtpUser#id} of user making trip request.
     */
    public String userId;

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    /**
     * From place. Trip starting point.
     */
    public String fromPlace;

    /**
     * To place. Trip end point.
     */
    public String toPlace;

    public AnonymizedTripRequest(String userId, String batchId, String fromPlace, String toPlace) {
        this.userId = userId;
        this.batchId = batchId;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
    }

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripRequest() {
    }
}
