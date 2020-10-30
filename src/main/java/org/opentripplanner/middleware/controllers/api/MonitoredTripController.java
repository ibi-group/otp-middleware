package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import spark.Request;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
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
        initializeTripAndSetVerifiedItinerary(monitoredTrip, req);
        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        // Authorization checks are done prior to this hook
        return true;
    }

    /**
     * Helper code for the preCreateHook method that
     * - initializes a {@link MonitoredTrip} instance,
     * - checks that that trip's itinerary exists for that trip's monitored days,
     * - sets the trip with a non-realtime version of it.
     */
    private void initializeTripAndSetVerifiedItinerary(MonitoredTrip monitoredTrip, Request req) {
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
        ItineraryExistence checkResult = checkItineraryExistence(monitoredTrip, req);

        // Replace the provided trip's itinerary with a verified, non-realtime version of it.
        if (checkResult != null) {
            updateTripWithVerifiedItinerary(monitoredTrip, req, checkResult);
        }
    }

    /**
     * Checks that non-realtime itineraries exist for the days the specified monitored trip is active.
     */
    private static ItineraryExistence checkItineraryExistence(MonitoredTrip trip, Request request) {
        try {
            ItineraryExistence checkResult = ItineraryUtils.checkItineraryExistence(trip, false);
            if (!checkResult.allCheckedDatesAreValid()) {
                logMessageAndHalt(
                    request,
                    HttpStatus.BAD_REQUEST_400,
                    "An itinerary does not exist for some of the monitored days for the requested trip."
                );
            }
            return checkResult;
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
        return null;
    }

    /**
     * Replace the itinerary provided with the monitored trip
     * with a non-real-time, verified itinerary from the responses provided.
     */
    private static void updateTripWithVerifiedItinerary(MonitoredTrip monitoredTrip, Request request, ItineraryExistence responsesByDayOfWeek) {
        try {
            Map<String, String> params = monitoredTrip.parseQueryParams();
            String queryDateParam = params.get(DATE_PARAM);

            // TODO: Refactor. Originally found in ItineraryUtils#getDatesToCheckItineraryExistence.
            LocalDate queryDate = DateTimeUtils.getDateFromQueryDateString(queryDateParam);

            // Find the response corresponding to the day of the query.
            // TODO/FIXME: There is a possibility that the user chooses to monitor the query/trip provided
            //       on other days but not the day for which the plan request was originally made.
            //       In such cases, the actual itinerary can be different from the one we are looking to save.
            //       To address that, in the UI, we can, for instance, force the date for the plan request to be monitored.
            ItineraryExistence.ItineraryExistenceResult itinExistenceForQueryDay = responsesByDayOfWeek.getResultForDayOfWeek(queryDate.getDayOfWeek());
            if (itinExistenceForQueryDay != null &&
                itinExistenceForQueryDay.isValid &&
                itinExistenceForQueryDay.itinerary != null)
            {
                monitoredTrip.itinerary = itinExistenceForQueryDay.itinerary;
                monitoredTrip.initializeFromItineraryAndQueryParams();
            }
        } catch (URISyntaxException e) { // triggered by monitoredTrip#parseQueryParams.
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
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
}
