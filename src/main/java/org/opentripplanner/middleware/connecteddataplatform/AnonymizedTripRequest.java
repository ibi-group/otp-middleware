package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.utils.LatLongUtils;

/**
 * Class to hold anonymized trip requests only.
 */
public class AnonymizedTripRequest {

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    /**
     * From place Lat. Trip starting point.
     */
    public Double fromPlaceLat;

    /**
     * From place Lon. Trip starting point.
     */
    public Double fromPlaceLon;

    /**
     * To place Lat. Trip end point.
     */
    public Double toPlaceLat;

    /**
     * To place Lon. Trip end point.
     */
    public Double toPlaceLon;

    /** The date the trip request was made. */
    public String date;

    /** The time the trip request was made. */
    public String time;

    /** Either 'arrive by' or 'depart at'. */
    public String tripType;

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
        LatLongUtils.Coordinates fromCoordinates,
        LatLongUtils.Coordinates toCoordinates
    ) {
        this.batchId = tripRequest.batchId;
        this.fromPlaceLat = fromCoordinates.latitude;
        this.fromPlaceLon = fromCoordinates.longitude;
        this.toPlaceLat = toCoordinates.latitude;
        this.toPlaceLon = toCoordinates.longitude;
        if (tripRequest. requestParameters != null) {
            this.date = tripRequest.requestParameters.get("date");
            this.time = tripRequest.requestParameters.get("time");
            this.tripType = tripRequest.requestParameters.get("arriveBy");
            if (tripType != null && tripType.equalsIgnoreCase("true")) {
                tripType = "Arrive By";
            } else if (tripType != null && tripType.equalsIgnoreCase("false")) {
                tripType = "Depart At";
            }
            this.mode = tripRequest.requestParameters.get("mode");
            this.maxWalkDistance = tripRequest.requestParameters.get("maxWalkDistance");
            this.optimize = tripRequest.requestParameters.get("optimize");
        }
    }
}
