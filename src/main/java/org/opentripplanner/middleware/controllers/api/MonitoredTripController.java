package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.net.URISyntaxException;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing {@link MonitoredTrip} entities. This
 * controller connects with Auth0 services using the hooks provided by {@link ApiController}.
 */
public class MonitoredTripController extends ApiController<MonitoredTrip> {
    private static final int MAXIMUM_PERMITTED_MONITORED_TRIPS
        = getConfigPropertyAsInt("MAXIMUM_PERMITTED_MONITORED_TRIPS", 5);

    public MonitoredTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrips, "secure/monitoredtrip");
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        // Add the api key route BEFORE the regular CRUD methods
        ApiEndpoint modifiedEndpoint = baseEndpoint
            .post(path("/checkitinerary")
                    .withDescription("Returns the itinerary existence check results for a monitored trip.")
                    .withRequestType(MonitoredTrip.class)
                    .withProduces(JSON_ONLY)
                    .withResponseType(ItineraryExistence.class),
                MonitoredTripController::checkItinerary, JsonUtils::toJson);
        // Add the regular CRUD methods after defining the controller-specific routes.
        super.buildEndpoint(modifiedEndpoint);
    }

    /**
     * Before creating a {@link MonitoredTrip}, check that the itinerary associated with the trip exists on the selected
     * days of the week. Update the itinerary if everything looks OK, otherwise halt the request.
     */
    @Override
    MonitoredTrip preCreateHook(MonitoredTrip monitoredTrip, Request req) {
        // Ensure user has not reached their limit for number of trips.
        verifyBelowMaxNumTrips(monitoredTrip.userId, req);
        checkTripCanBeMonitored(monitoredTrip, req);
        processTripQueryParams(monitoredTrip, req);
        
        try {
            // Check itinerary existence and replace the provided trip's itinerary with a verified, non-realtime
            // version of it.
            boolean success = monitoredTrip.checkItineraryExistence(false, true);
            if (!success) {
                logMessageAndHalt(
                    req,
                    HttpStatus.BAD_REQUEST_400,
                    monitoredTrip.itineraryExistence.message
                );
            }
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }

        return monitoredTrip;
    }

    /**
     * Processes the {@link MonitoredTrip} query parameters, so the trip's fields match the query parameters.
     * If an error occurs regarding the query params, returns a HTTP 400 status.
     */
    private void processTripQueryParams(MonitoredTrip monitoredTrip, Request req) {
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
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        checkTripCanBeMonitored(monitoredTrip, req);
        processTripQueryParams(monitoredTrip, req);

        // TODO: Update itinerary existence record when updating a trip.

        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        // Authorization checks are done prior to this hook
        return true;
    }

    /**
     * Check itinerary existence by making OTP requests on all days of the week.
     * @return The results of the itinerary existence check.
     */
    private static ItineraryExistence checkItinerary(Request request, Response response) {
        MonitoredTrip trip;
        try {
            trip = getPOJOFromRequestBody(request, MonitoredTrip.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON for MonitoredTrip", e);
            return null;
        }
        try {
            trip.initializeFromItineraryAndQueryParams();
            trip.checkItineraryExistence(true, false);
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
        return trip.itineraryExistence;
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
     * Checks that the given {@link MonitoredTrip} can be monitored (i.e., that the underlying
     * {@link org.opentripplanner.middleware.otp.response.Itinerary} can be monitored).
     */
    private void checkTripCanBeMonitored(MonitoredTrip trip, Request request) {
        Itinerary.ItineraryCanBeMonitored canBeMonitored = trip.itinerary.assessCanBeMonitored();
        if (!canBeMonitored.overall) {
            logMessageAndHalt(
                request,
                HttpStatus.BAD_REQUEST_400,
                canBeMonitored.getMessage()
            );
        }
    }
}
