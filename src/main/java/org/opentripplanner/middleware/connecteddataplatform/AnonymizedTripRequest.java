package org.opentripplanner.middleware.connecteddataplatform;

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
     * From place. Trip starting point.
     */
    public String fromPlace;

    /**
     * To place. Trip end point.
     */
    public String toPlace;

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

    public AnonymizedTripRequest(
        String batchId,
        String fromPlace,
        boolean fromPlaceIsPublic,
        String toPlace,
        boolean toPlaceIsPublic,
        HashMap<String, String> requestParameters
    ) {
        this.batchId = batchId;
        this.fromPlace = getPlaceCoordinates(fromPlaceIsPublic, fromPlace);
        this.toPlace = getPlaceCoordinates(toPlaceIsPublic, toPlace);
        if (requestParameters != null) {
            this.date = requestParameters.get("date");
            this.time = requestParameters.get("time");
            this.arriveBy = requestParameters.get("arriveBy");
            if (arriveBy != null && arriveBy.equalsIgnoreCase("true")) {
                arriveBy = "Arrive By";
            } else if (arriveBy != null && arriveBy.equalsIgnoreCase("false")) {
                arriveBy = "Depart At";
            }
            this.mode = requestParameters.get("mode");
            this.maxWalkDistance = requestParameters.get("maxWalkDistance");
            this.optimize = requestParameters.get("optimize");
        }
    }

    /**
     * This method extracts the lat/long values from the place value. The place value is assumed to be in the format
     * 'location :: lat,long'. If the place is deemed to be public, return the coordinates as provided by OTP. If not,
     * randomize and return.
     */
    private String getPlaceCoordinates(boolean isPublic, String place) {
        String coordinates = place.split("::")[1].trim();
        return isPublic ? coordinates : getRandomizedCoordinates(coordinates);
    }

    /**
     * This method randomizes the provided coordinates and returns it as a comma separated pair.
     */
    private String getRandomizedCoordinates(String coords) {
        LatLongUtils.Coordinates coordinates = new LatLongUtils.Coordinates(
            Double.parseDouble(coords.split(",")[0]),
            Double.parseDouble(coords.split(",")[1])
        );
        LatLongUtils.Coordinates randomized = LatLongUtils.getRandomizedCoordinates(coordinates);
        return randomized.latitude + "," + randomized.longitude;
    }
}
