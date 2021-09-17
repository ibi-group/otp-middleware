package org.opentripplanner.middleware.connecteddataplatform;

/**
 * Class to hold anonymized trip requests only.
 */
public class AnonymizedTripRequest {
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

    public AnonymizedTripRequest(String batchId, String fromPlace, String toPlace) {
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
