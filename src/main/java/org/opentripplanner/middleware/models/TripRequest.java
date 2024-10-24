package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mongodb.client.FindIterable;
import org.opentripplanner.middleware.otp.graphql.QueryVariables;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.persistence.TypedPersistence.filterByUserId;

/**
 * A trip request represents an OTP UI trip request (initiated by a user) destined for an OpenTripPlanner instance.
 * otp-middleware stores these trip requests for reporting purposes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    /**
     * The variables passed to the OTP GraphQL `plan` request that triggered this response.
     */
    public QueryVariables otp2QueryParams;

    /**
     * This no-arg constructor exists to make MongoDB happy.
     */
    public TripRequest() {
    }

    public TripRequest(
        String userId,
        String batchId,
        QueryVariables otp2QueryParams
    ) {
        this.userId = userId;
        this.batchId = batchId;
        this.fromPlace = otp2QueryParams.fromPlace;
        this.toPlace = otp2QueryParams.toPlace;
        this.otp2QueryParams = otp2QueryParams;
    }

    @Override
    public String toString() {
        return "TripRequest{" +
            "userId='" + userId + '\'' +
            ", batchId='" + batchId + '\'' +
            ", fromPlace='" + fromPlace + '\'' +
            ", toPlace='" + toPlace + '\'' +
            ", otp2QueryParams=" + JsonUtils.toJson(otp2QueryParams) +
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
