package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.ApiEndpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.tripmonitor.jobs.MonitoredTripLocks;
import org.opentripplanner.middleware.utils.InvalidItineraryReason;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;
import spark.Request;
import spark.Response;

import java.util.Set;
import java.util.stream.Collectors;

import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
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
                    .withResponses(SwaggerUtils.createStandardResponses(ItineraryExistence.class)),
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
        preCreateOrUpdateChecks(monitoredTrip, req);

        // FIXME: Pending https://github.com/ibi-group/otp-middleware/pull/219,
        //   check itinerary existence for recurring trips only for now.
        //   (Existence should ultimately be checked on all trips.)
        if (!monitoredTrip.isOneTime()) {
            // Check itinerary existence for all days and replace the provided trip's itinerary with a verified,
            // non-realtime version of it.
            boolean success = monitoredTrip.checkItineraryExistence(true);
            if (!success) {
                logMessageAndHalt(
                    req,
                    HttpStatus.BAD_REQUEST_400,
                    monitoredTrip.itineraryExistence.message
                );
            }
        }

        return monitoredTrip;
    }

    /**
     * Run a CheckMonitoredTrip job immediately after creation.
     */
    @Override
    MonitoredTrip postCreateHook(MonitoredTrip monitoredTrip, Request req) {
        try {
            MonitoredTripLocks.lock(monitoredTrip.id);
            return runCheckMonitoredTrip(monitoredTrip);
        } catch (Exception e) {
            // FIXME: an error happened while checking the trip, but the trip was saved to the DB, so return the raw
            //  trip as it was saved in the db?
            return monitoredTrip;
        } finally {
            MonitoredTripLocks.unlock(monitoredTrip.id);
        }
    }

    /**
     * Creates and runs a check monitored trip job for the specified monitoredTrip. This method assumes that the proper
     * monitored trip locks are created and removed elsewhere. The monitored trip can be modified during the check
     * monitored trip job, so return the trip as found in the database after the job completes.
     */
    private MonitoredTrip runCheckMonitoredTrip(MonitoredTrip monitoredTrip) throws Exception {
        new CheckMonitoredTrip(monitoredTrip, false).run();
        return Persistence.monitoredTrips.getById(monitoredTrip.id);
    }

    /**
     * Performs the operations/checks common to the preCreate and preUpdate hooks.
     */
    private void preCreateOrUpdateChecks(MonitoredTrip monitoredTrip, Request req) {
        checkTripCanBeMonitored(monitoredTrip, req);
        processTripQueryParams(monitoredTrip, req);
    }

    /**
     * Processes the {@link MonitoredTrip} query parameters, so the trip's fields match the query parameters.
     * If an error occurs regarding the query params, returns a HTTP 400 status.
     */
    private void processTripQueryParams(MonitoredTrip monitoredTrip, Request req) {
        try {
            monitoredTrip.initializeFromItineraryAndQueryParams(req);
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
        // lock the trip so that the a CheckMonitoredTrip job won't concurrently analyze/update the trip.
        MonitoredTripLocks.lockTripForUpdating(monitoredTrip, req);

        try {
            // Forbid the editing of certain values that are analyzed and set during the CheckMonitoredTrip job.
            // These include the itinerary, journeyState, and fields initially computed via processTripQueryParams
            // that we don't need to recalculate.
            // Note: There is no need to re-check for monitorability because the itinerary field cannot be changed.
            monitoredTrip.itinerary = preExisting.itinerary;
            monitoredTrip.journeyState = preExisting.journeyState;
            monitoredTrip.itineraryExistence = preExisting.itineraryExistence;
            monitoredTrip.tripTime = preExisting.tripTime;
            monitoredTrip.queryParams = preExisting.queryParams;
            monitoredTrip.userId = preExisting.userId;
            monitoredTrip.from = preExisting.from;
            monitoredTrip.to = preExisting.to;
            monitoredTrip.arriveBy = preExisting.arriveBy;

            // TODO: Update itinerary existence record when updating a trip?
            //   (Currently, we let web requests change the monitored days regardless of existence.)

            // perform the database update here before releasing the lock to be sure that the record is updated in the
            // database before a CheckMonitoredTripJob analyzes the data
            Persistence.monitoredTrips.replace(monitoredTrip.id, monitoredTrip);
            return runCheckMonitoredTrip(monitoredTrip);
        } catch (Exception e) {
            // FIXME: an error happened while updating the trip, but the trip might have been saved to the DB, so return
            //  the raw trip as it was saved in the db before the check monitored trip job ran?
            return monitoredTrip;
        } finally {
            MonitoredTripLocks.unlock(monitoredTrip.id);
        }
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
        trip.initializeFromItineraryAndQueryParams(trip.otp2QueryParams);
        trip.checkItineraryExistence(false);
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
        Set<InvalidItineraryReason> invalidReasons = trip.itinerary.checkItineraryCanBeMonitored();
        if (!invalidReasons.isEmpty()) {
            String reasonsString = invalidReasons.stream()
                .map(InvalidItineraryReason::getMessage)
                .collect(Collectors.joining(", "));
            logMessageAndHalt(
                request,
                HttpStatus.BAD_REQUEST_400,
                String.format("The trip cannot be monitored: %s", reasonsString)
            );
        }
    }
}
