package org.opentripplanner.middleware.models;

import com.mongodb.client.FindIterable;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.persistence.TypedPersistence.filterByUserId;

/**
 * A trip request represents an OTP UI trip request (initiated by a user) destined for an OpenTripPlanner instance.
 * otp-middleware stores these trip requests for reporting purposes.
 */
public class TripRequest extends Model {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TripRequest.class);

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

    /** A dictionary of the parameters provided in the request that triggered this response. */
    public Map<String, String> requestParameters;

    public OtpGraphQLVariables otp2QueryParams;

    /**
     * This no-arg constructor exists to make MongoDB happy.
     */
    public TripRequest() {
    }

    public TripRequest(
        String userId,
        String batchId,
        String fromPlace,
        String toPlace,
        Map<String, String> requestParameters
    ) {
        this.userId = userId;
        this.batchId = batchId;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.requestParameters = requestParameters;
    }

    public TripRequest(
        String userId,
        String batchId,
        String fromPlace,
        String toPlace,
        OtpGraphQLVariables otp2QueryParams
    ) {
        this.userId = userId;
        this.batchId = batchId;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.otp2QueryParams = otp2QueryParams;
    }

    @Override
    public String toString() {
        return "TripRequest{" +
            "userId='" + userId + '\'' +
            ", batchId='" + batchId + '\'' +
            ", fromPlace='" + fromPlace + '\'' +
            ", toPlace='" + toPlace + '\'' +
            ", requestParameters=" + requestParameters +
            '}';
    }

    /**
     * Get all trip requests for a given user id.
     */
    public static FindIterable<TripRequest> requestsForUser(String userId) {
        return Persistence.tripRequests.getFiltered(filterByUserId(userId));
    }

    @Override
    public boolean delete() {
        boolean summariesDeleted = Persistence.tripSummaries.removeFiltered(eq("tripRequestId", this.id));
        if (!summariesDeleted) {
            LOG.error("Could not delete linked trip summary for request ID {}", this.id);
        }
        return Persistence.tripRequests.removeById(this.id);
    }
}
