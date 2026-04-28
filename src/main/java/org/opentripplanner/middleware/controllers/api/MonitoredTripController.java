package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.ApiEndpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.tripmonitor.jobs.MonitoredTripLocks;
import org.opentripplanner.middleware.utils.InvalidItineraryReason;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.i18n.Message.TRIP_INVITE_COMPANION;
import static org.opentripplanner.middleware.i18n.Message.TRIP_INVITE_OBSERVER;
import static org.opentripplanner.middleware.i18n.Message.TRIP_INVITE_PRIMARY_TRAVELER;
import static org.opentripplanner.middleware.models.MonitoredTrip.USER_ID_FIELD_NAME;
import static org.opentripplanner.middleware.models.MonitoredTrip.getAddedUsers;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;


/**
 * Implementation of the {@link ApiController} abstract class for managing {@link MonitoredTrip} entities. This
 * controller connects with Auth0 services using the hooks provided by {@link ApiController}.
 */
public class MonitoredTripController extends ApiController<MonitoredTrip> {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoredTripController.class);

    private static final int MAXIMUM_PERMITTED_MONITORED_TRIPS
        = getConfigPropertyAsInt("MAXIMUM_PERMITTED_MONITORED_TRIPS", 5);

    public static final String MONITORED_TRIP_PATH = "secure/monitoredtrip";

    public static final String CHECK_ITINERARY_SUBPATH = "/checkitinerary";

    /**
     * Size of the cached itinerary checks that we should not repeat.
     */
    private static final int MAXIMUM_EXISTENCE_CHECKS = 100;

    /**
     * Caches a number of recent ItineraryExistence with their ids. Implements a
     * <a href="https://stackoverflow.com/questions/1963806/is-there-a-fixed-sized-queue-which-removes-excessive-elements">
     *     fixed-sized queue trick
     * </a>
     * This approach loses its effect if many calls are made to /checkitinerary beyond MAXIMUM_CHECKS without
     * a subsequent POST to actually persist the itinerary.
     */
    private static final LinkedHashMap<String, ItineraryExistence> checksPerformed = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItineraryExistence> eldest) {
            return this.size() > MAXIMUM_EXISTENCE_CHECKS;
        }
    };

    public MonitoredTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrips, MONITORED_TRIP_PATH);
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        // Add the api key route BEFORE the regular CRUD methods
        ApiEndpoint modifiedEndpoint = baseEndpoint
            .post(path(CHECK_ITINERARY_SUBPATH)
                    .withDescription("Returns the itinerary existence check results for a monitored trip.")
                    .withRequestType(MonitoredTrip.class)
                    .withProduces(JSON_ONLY)
                    .withResponses(SwaggerUtils.createStandardResponses(ItineraryExistence.class)),
                MonitoredTripController::checkItinerary, JsonUtils::toJson);
        // Add the regular CRUD methods after defining the controller-specific routes.
        super.buildEndpoint(modifiedEndpoint);
    }

    @Override
    protected Bson getEntityFilter(OtpUser user) {
        return Filters.or(
            Filters.eq(USER_ID_PARAM, user.id),
            Filters.eq("primary.userId", user.id),
            Filters.eq("companion.email", user.email),
            Filters.eq("observers.email", user.email)
        );
    }

    /**
     * Before creating a {@link MonitoredTrip}, check that the itinerary associated with the trip exists on the selected
     * days of the week. Update the itinerary if everything looks OK, otherwise halt the request.
     * If a check has already been performed (the caller populates the itineraryExistence field including the id of
     * such check), and the check shows the itinerary exists, skip the check.
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
            String checkId = monitoredTrip.itineraryExistence != null ? monitoredTrip.itineraryExistence.id : null;
            ItineraryExistence previousExistence = checksPerformed.get(checkId);
            if (previousExistence != null) {
                if (previousExistence.allMonitoredDaysAreValid(monitoredTrip)) {
                    LOG.info("Skipping itinerary check in preCreateHook because we have already checked it exists.");
                    monitoredTrip.itineraryExistence = previousExistence;
                    monitoredTrip.updateTripWithVerifiedItinerary();

                    // Consume (remove) the check
                    checksPerformed.remove(checkId);
                } else {
                    logMessageAndHalt(
                        req,
                        HttpStatus.BAD_REQUEST_400,
                        previousExistence.message
                    );
                }
            } else {
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
        }

        notifyTripCompanionsAndObservers(monitoredTrip, null);

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
        new CheckMonitoredTrip(monitoredTrip).run();
        return Persistence.monitoredTrips.getById(monitoredTrip.id);
    }

    /**
     * Performs the operations/checks common to the preCreate and preUpdate hooks.
     */
    private void preCreateOrUpdateChecks(MonitoredTrip monitoredTrip, Request req) {
        checkTripCanBeMonitored(monitoredTrip, req);
        processTripQueryParams(monitoredTrip, req);
    }

    /** Notify users added as companions or observers to a trip. (Removed users won't get notified.) */
    private void notifyTripCompanionsAndObservers(MonitoredTrip monitoredTrip, MonitoredTrip originalTrip) {
        MonitoredTrip.TripUsers usersToNotify = getAddedUsers(monitoredTrip, originalTrip);
        OtpUser tripCreator = Persistence.otpUsers.getById(monitoredTrip.userId);

        if (usersToNotify.companion != null) {
            OtpUser companionUser = Persistence.otpUsers.getOneFiltered(Filters.eq("email", usersToNotify.companion.email));
            NotificationUtils.notifyCompanion(monitoredTrip, tripCreator, companionUser, TRIP_INVITE_COMPANION);
        }

        if (usersToNotify.primary != null) {
            // email could be used too for primary users
            OtpUser primaryUser = Persistence.otpUsers.getById(usersToNotify.primary.userId);
            NotificationUtils.notifyCompanion(monitoredTrip, tripCreator, primaryUser, TRIP_INVITE_PRIMARY_TRAVELER);
        }

        if (!usersToNotify.observers.isEmpty()) {
            for (RelatedUser observer : usersToNotify.observers) {
                OtpUser observerUser = Persistence.otpUsers.getOneFiltered(Filters.eq("email", observer.email));
                NotificationUtils.notifyCompanion(monitoredTrip, tripCreator, observerUser, TRIP_INVITE_OBSERVER);
            }
        }
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
            monitoredTrip.otp2QueryParams = preExisting.otp2QueryParams;
            monitoredTrip.userId = preExisting.userId;
            monitoredTrip.from = preExisting.from;
            monitoredTrip.to = preExisting.to;

            // perform the database update here before releasing the lock to be sure that the record is updated in the
            // database before a CheckMonitoredTripJob analyzes the data
            Persistence.monitoredTrips.replace(monitoredTrip.id, monitoredTrip);

            notifyTripCompanionsAndObservers(monitoredTrip, preExisting);

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
        boolean isNewTrip = Persistence.monitoredTrips.getCountFiltered(eq(trip.id)) == 0;
        if (isNewTrip) {
            checksPerformed.put(trip.itineraryExistence.id, trip.itineraryExistence);
        } else {
            Persistence.monitoredTrips.replace(trip.id, trip);
        }
        return trip.itineraryExistence;
    }

    /**
     * Confirm that the maximum number of saved monitored trips has not been reached
     */
    private void verifyBelowMaxNumTrips(String userId, Request request) {
        // filter monitored trip on user id to find out how many have already been saved
        Bson filter = Filters.and(eq(USER_ID_FIELD_NAME, userId));
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

    /**
     * For use in tests only.
     */
    static void simulateExistenceCheck(ItineraryExistence existence) {
        checksPerformed.put(existence.id, existence);
    }

    /**
     * For use in tests only.
     */
    static int getChecksSize() {
        return checksPerformed.size();
    }
}
