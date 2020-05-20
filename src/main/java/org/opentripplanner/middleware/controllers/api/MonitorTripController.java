package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public class MonitorTripController extends ApiController<MonitoredTrip> {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorTripController.class);

    public MonitorTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrip, "secure/monitortrip");
    }

    @Override
    MonitoredTrip preCreateHook(MonitoredTrip monitoredTrip, Request req) {
        // Confirm that the user exists before creating monitored trip
        isValidUser(monitoredTrip.userId, req);

        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        // Confirm that the user exists before creating monitored trip
        isValidUser(monitoredTrip.userId, req);

        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        return true;
    }

    // Confirm that the user exists before creating monitored trip
    private void isValidUser(String userId, Request req) {
        User user = Persistence.users.getById(userId);
        if (user == null) {
            LOG.error("User with id {} does not exist", userId);
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "User does not exist.");
        }
    }
}
