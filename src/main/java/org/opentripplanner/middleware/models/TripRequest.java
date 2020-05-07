package org.opentripplanner.middleware.models;

import java.util.Date;

/**
 * A trp request represents a OTP UI trip request initiated by a user of an OpenTripPlanner instance.
 * otp-middleware stores these trip request for trip monitoring purposes.
 */
public class TripRequest extends Model {
    private static final long serialVersionUID = 1L;

    /**
     * User Id. Id of user making trip request.
     */
    private String userId;

    /**
     * Batch Id. Id for trip plans of different modes.
     */
    private String batchId;

    /**
     * Time stamp. Time at which the request was made.
     */
    // TODO: Perhaps not needed as Model -> dateCreated would be the same
    private Date timestamp;

    /**
     * From place. Trip starting point.
     */
    private String fromPlace;

    /**
     * To place. Trip end point.
     */
    private String toPlace;

    /**
     * Query params. Query parameters influencing trip.
     */
    private String queryParams;

    public TripRequest() {
    }

    public TripRequest(String userId, String batchId, String fromPlace, String toPlace, String queryParams) {
        this.userId = userId;
        this.batchId = batchId;
        this.timestamp = new Date();
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.queryParams = queryParams;
    }

    public String getUserId() {
        return userId;
    }

    public String getBatchId() {
        return batchId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getFromPlace() {
        return fromPlace;
    }

    public String getToPlace() {
        return toPlace;
    }

    public String getQueryParams() {
        return queryParams;
    }

    @Override
    public String toString() {
        return "TripRequest{" +
                "userId='" + userId + '\'' +
                ", batchId='" + batchId + '\'' +
                ", timestamp=" + timestamp +
                ", fromPlace='" + fromPlace + '\'' +
                ", toPlace='" + toPlace + '\'' +
                ", queryParams='" + queryParams + '\'' +
                '}';
    }
}
