package org.opentripplanner.middleware.models;

/**
 * A trip request represents an OTP UI trip request (initiated by a user) destine for an OpenTripPlanner instance.
 * otp-middleware stores these trip requests for trip monitoring purposes.
 */
public class TripRequest extends Model {
    private static final long serialVersionUID = 1L;

    /**
     * User Id. Id of user making trip request.
     */
    public String userId;

    /**
     * Batch Id. Id for trip plans of different modes.
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

    /**
     * Query params. Query parameters influencing trip.
     */
    //TODO: This could be the request parameters returned as part of the plan response. Would be POJO based instead of just text.
    public String queryParams;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripRequest() {
    }

    public TripRequest(String userId, String batchId, String fromPlace, String toPlace, String queryParams) {
        this.userId = userId;
        this.batchId = batchId;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.queryParams = queryParams;
    }

    @Override
    public String toString() {
        return "TripRequest{" +
            "userId='" + userId + '\'' +
            ", batchId='" + batchId + '\'' +
            ", fromPlace='" + fromPlace + '\'' +
            ", toPlace='" + toPlace + '\'' +
            ", queryParams='" + queryParams + '\'' +
            '}';
    }
}
