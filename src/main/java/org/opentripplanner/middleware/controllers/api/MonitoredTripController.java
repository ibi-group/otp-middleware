package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing monitored trips. This controller connects
 * with Auth0 services using the hooks provided by {@link ApiController}.
 */
public class MonitoredTripController extends ApiController<MonitoredTrip> {
    private static final int MAXIMUM_PERMITTED_MONITORED_TRIPS
        = getConfigPropertyAsInt("MAXIMUM_PERMITTED_MONITORED_TRIPS", 5);

    public MonitoredTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrips, "secure/monitoredtrip");
    }

    @Override
    MonitoredTrip preCreateHook(MonitoredTrip monitoredTrip, Request req) {
        verifyBelowMaxNumTrips(monitoredTrip.userId, req);
        checkItineraryCanBeMonitored(monitoredTrip, req);
        try {
            monitoredTrip.initializeFromItineraryAndQueryParams();
        } catch (Exception e) {
            logMessageAndHalt(
                req,
                HttpStatus.BAD_REQUEST_400,
                "Invalid input data received for monitored trip.",
                e
            );
        }
        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        try {
            monitoredTrip.initializeFromItineraryAndQueryParams();
        } catch (Exception e) {
            logMessageAndHalt(
                req,
                HttpStatus.BAD_REQUEST_400,
                "Invalid input data received for monitored trip.",
                e
            );
        }
        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        // Authorization checks are done prior to this hook
        return true;
    }

    /**
     * Confirm that the maximum number of saved monitored trips has not been reached
     */
    private void verifyBelowMaxNumTrips(String userId, Request request) {
        // filter monitored trip on user id to find out how many have already been saved
        Bson filter = Filters.and(eq("userId", userId));
        long count = this.persistence.getCountFiltered(filter);
        if (count >= MAXIMUM_PERMITTED_MONITORED_TRIPS) {
            logMessageAndHalt(
                request,
                HttpStatus.BAD_REQUEST_400,
                "Maximum permitted saved monitored trips reached. Maximum = " + MAXIMUM_PERMITTED_MONITORED_TRIPS
            );
        }
    }

    /**
     * Checks that the given {@link MonitoredTrip} can be monitored
     * (the underlying {@link org.opentripplanner.middleware.otp.response.Itinerary} has transit and no rentals).
     */
    private void checkItineraryCanBeMonitored(MonitoredTrip trip, Request request) {
        if (!ItineraryUtils.itineraryHasTransitAndNoRentals(trip.itinerary)) {
            logMessageAndHalt(
                request,
                HttpStatus.BAD_REQUEST_400,
                "Only trips with an itinerary that includes transit and no rentals can be monitored."
            );
        }
    }
}
