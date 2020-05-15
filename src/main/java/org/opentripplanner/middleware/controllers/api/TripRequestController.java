package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

/**
 * Implementation of the {@link ApiController} abstract class for managing trip requests.
 */
public class TripRequestController extends ApiController<TripRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(TripRequestController.class);

    public TripRequestController(String apiPrefix){
        super(apiPrefix, Persistence.tripRequest, "triprequests");
    }

    @Override
    TripRequest preCreateHook(TripRequest tripRequest, Request req) {
        return tripRequest;
    }

    @Override
    TripRequest preUpdateHook(TripRequest tripRequest, TripRequest preExistingTripRequest, Request req) {
        return tripRequest;
    }

    @Override
    boolean preDeleteHook(TripRequest tripRequest, Request req) {
        return true;
    }

}
