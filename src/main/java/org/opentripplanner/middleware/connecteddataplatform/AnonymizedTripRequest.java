package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.utils.LatLongUtils;

import java.util.HashMap;

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

    public String arriveBy;

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

    public AnonymizedTripRequest(TripRequest tripRequest) {
        this.batchId = tripRequest.batchId;
        LatLongUtils.Coordinates fromCoords = getPlaceCoordinates(tripRequest.fromPlaceIsPublic, tripRequest.fromPlace);
        this.fromPlaceLat = fromCoords.latitude;
        this.fromPlaceLon = fromCoords.longitude;
        LatLongUtils.Coordinates toCoords = getPlaceCoordinates(tripRequest.toPlaceIsPublic, tripRequest.toPlace);
        this.toPlaceLat = toCoords.latitude;
        this.toPlaceLon = toCoords.longitude;
        if (tripRequest. requestParameters != null) {
            this.date = tripRequest.requestParameters.get("date");
            this.time = tripRequest.requestParameters.get("time");
            this.arriveBy = tripRequest.requestParameters.get("arriveBy");
            if (arriveBy != null && arriveBy.equalsIgnoreCase("true")) {
                arriveBy = "Arrive By";
            } else if (arriveBy != null && arriveBy.equalsIgnoreCase("false")) {
                arriveBy = "Depart At";
            }
            this.mode = tripRequest.requestParameters.get("mode");
            this.maxWalkDistance = tripRequest.requestParameters.get("maxWalkDistance");
            this.optimize = tripRequest.requestParameters.get("optimize");
        }
    }

    /**
     * This method extracts the lat/long values from the place value. The place value is assumed to be in the format
     * 'location :: lat,long'. If the place is deemed to be public, return the coordinates as provided by OTP. If not,
     * randomize and return.
     */
    private LatLongUtils.Coordinates getPlaceCoordinates(boolean isPublic, String place) {
        String coords = place.split("::")[1].trim();
        LatLongUtils.Coordinates coordinates = new LatLongUtils.Coordinates(
            Double.parseDouble(coords.split(",")[0]),
            Double.parseDouble(coords.split(",")[1])
        );
        return isPublic ? coordinates : LatLongUtils.getRandomizedCoordinates(coordinates);
    }
}
