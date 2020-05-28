package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Utils;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing monitored trips. This controller connects
 * with Auth0 services using the hooks provided by {@link ApiController}.
 */
public class MonitorTripController extends ApiController<MonitoredTrip> {

    public MonitorTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrip, "secure/monitortrip");
    }

    @Override
    MonitoredTrip preCreateHook(MonitoredTrip monitoredTrip, Request req) {
        isValidUser(monitoredTrip.userId, req);
        reachedMaximum(monitoredTrip.userId, req);

        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        isValidUser(monitoredTrip.userId, req);

        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        isValidUser(monitoredTrip.userId, req);
        return true;
    }

    /**
     * Confirm that the user exists and is authorized
     */
    private void isValidUser(String userId, Request request) {

        User user = Persistence.users.getById(userId);
        if (user == null) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unknown user.");
        }

        Auth0Utils.isAuthorizedUser(user, request);

    }

    /**
     * Confirm that the maximum number of saved monitored trips has not been reached
     */
    private void reachedMaximum(String userId, Request request) {
        long maximum = 5;

        // filter monitored trip on user id to find out how many have already been saved
        Bson filter = Filters.and(eq("userId", userId));
        long count = this.persistence.getCountFiltered(filter);
        if (count == maximum) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Maximum amount of saved monitored trips reached. Maximum = " + maximum);
        }
    }
}
