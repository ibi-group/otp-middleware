package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.utils.Coordinates;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.models.TripRequest} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedTripRequest {

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    /**
     * From place. Trip starting point.
     */
    public Coordinates fromPlace;

    /**
     * To place. Trip end point.
     */
    public Coordinates toPlace;

    /** The date the trip request was made. */
    public String date;

    /** The time the trip request was made. */
    public String time;

    /** Either 'arrive by' or 'depart at'. */
    public AnonymousTripType tripType;

    /** Transit modes selected by user. */
    public String mode;

    /** Maximum walking distance defined by user. */
    public String maxWalkDistance;

    /** Trip optimization specified by user. */
    public String optimize;

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripRequest() {
    }

    public AnonymizedTripRequest(
        TripRequest tripRequest,
        Coordinates fromCoordinates,
        Coordinates toCoordinates
    ) {
        this.batchId = tripRequest.batchId;
        this.fromPlace = fromCoordinates;
        this.toPlace = toCoordinates;
        if (tripRequest. requestParameters != null) {
            this.date = tripRequest.requestParameters.get("date");
            this.time = tripRequest.requestParameters.get("time");
            String tripType = tripRequest.requestParameters.get("arriveBy");
            if (tripType != null && tripType.equalsIgnoreCase("true")) {
                this.tripType = AnonymousTripType.ARRIVE_BY;
            } else if (tripType != null && tripType.equalsIgnoreCase("false")) {
                this.tripType = AnonymousTripType.DEPART_AT;
            }
            this.mode = tripRequest.requestParameters.get("mode");
            this.maxWalkDistance = tripRequest.requestParameters.get("maxWalkDistance");
            this.optimize = tripRequest.requestParameters.get("optimize");
        }
    }
}
