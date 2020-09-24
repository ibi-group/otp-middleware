package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.ItineraryExistenceChecker;
import spark.Request;

import java.net.URISyntaxException;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;

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
        ItineraryExistenceChecker.Result checkResult = checkItineraryExistence(monitoredTrip, req);

        // Replace the provided trip's itinerary with a verified, non-real-time version of it.
        if (checkResult != null) {
            updateTripWithVerifiedItinerary(monitoredTrip, req, checkResult.labeledResponses);
        }
        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        try {
            monitoredTrip.initializeFromItineraryAndQueryParams();
          ItineraryExistenceChecker.Result checkResult = checkItineraryExistence(monitoredTrip, req);

          // Replace the provided trip's itinerary with a verified, non-real-time version of it.
          if (checkResult != null) {
              updateTripWithVerifiedItinerary(monitoredTrip, req, checkResult.labeledResponses);
          }
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
     * Checks that non-realtime itineraries exist for the days the specified monitored trip is active.
     */
    private static ItineraryExistenceChecker.Result checkItineraryExistence(MonitoredTrip trip, Request request) {
        ItineraryExistenceChecker itineraryChecker = new ItineraryExistenceChecker(OtpDispatcher::sendOtpPlanRequest);
        try {
            ItineraryExistenceChecker.Result checkResult = itineraryChecker.checkAll(ItineraryUtils.getItineraryExistenceQueries(trip, false), trip.isArriveBy());
            if (!checkResult.allItinerariesExist) {
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
    private static void updateTripWithVerifiedItinerary(MonitoredTrip monitoredTrip, Request request, Map<String, OtpResponse> responsesByDate) {
        try {
            Map<String, String> params = ItineraryUtils.getQueryParams(monitoredTrip.queryParams);
            String queryDate = params.get(DATE_PARAM);

            // Find the response corresponding to the day of the query.
            // TODO/FIXME: There is a possibility that the user chooses to monitor the query/trip provided
            //       on other days but not the day for which the plan request was originally made.
            //       In such cases, the actual itinerary can be different from the one we are looking to save.
            //       To address that, in the UI, we can, for instance, force the date for the plan request to be monitored.
            OtpResponse responseForDayOfQuery = responsesByDate.get(queryDate);
            if (responseForDayOfQuery != null) {
                if (responseForDayOfQuery.plan != null && responseForDayOfQuery.plan.itineraries != null) {
                    // TODO/FIXME: need a trip resemblance check to supplement the ui_activeItinerary param used in this function.
                    ItineraryUtils.updateTripWithVerifiedItinerary(monitoredTrip, responseForDayOfQuery.plan.itineraries);
                }
            }
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
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
