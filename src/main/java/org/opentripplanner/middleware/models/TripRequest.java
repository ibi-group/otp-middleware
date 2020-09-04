package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.persistence.TypedPersistence.filterByUserAndDateRange;
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

    /**
     * Query params. Query parameters influencing trip.
     */
    //TODO: This could be the request parameters returned as part of the plan response. Would be POJO based instead of just text.
    public String queryParams;

    /**
     * This no-arg constructor exists to make MongoDB happy.
     */
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
            ", id='" + id + '\'' +
            ", lastUpdated=" + lastUpdated +
            ", dateCreated=" + dateCreated +
            '}';
    }

    public static List<TripRequest> requestsForUser(String userId) {
        return Persistence.tripRequests.getFiltered(filterByUserId(userId));
    }

    public static List<TripRequest> requestsForUser(String userId, Date fromDate, Date toDate, int limit) {
        return Persistence.tripRequests.getFilteredWithLimit(filterByUserAndDateRange(userId, fromDate, toDate), limit);
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
