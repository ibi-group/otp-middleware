package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.ItineraryExistenceChecker;
import spark.Request;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        monitoredTrip.initializeFromItinerary();
        ItineraryExistenceChecker.Result checkResult = checkItineraryExistence(monitoredTrip, req);

        // Replace the provided trip's itinerary with a verified, non-real-time version of it.
        writeVerifiedItinerary(monitoredTrip, req, checkResult.responses);
        return monitoredTrip;
    }

    /**
     * Replace the itinerary provided with the monitored trip
     * with a non-real-time, verified itinerary from the responses provided.
     */
    private static void writeVerifiedItinerary(MonitoredTrip monitoredTrip, Request request, List<OtpDispatcherResponse> dispatcherResponses) {
        try {
            Map<String, String> params = ItineraryUtils.getQueryParams(monitoredTrip.queryParams);
            String queryDate = params.get(DATE_PARAM);
            List<Response> otpResponses = dispatcherResponses.stream()
                .map(OtpDispatcherResponse::getResponse)
                .collect(Collectors.toList());

            // Find the response corresponding to the day of the query.
            // TODO/FIXME: There is a possibility that the user chooses to monitor the query/trip provided
            //       on other days but not the day for which the plan request was originally made.
            //       To address that, in the UI, we can, for instance, force the date for the plan request to be monitored.
            Response responseForDayOfQuery = ItineraryUtils.getResponseForDate(otpResponses, queryDate);
            if (responseForDayOfQuery != null) {
                List<Itinerary> itineraries = responseForDayOfQuery.plan.itineraries;

                // TODO/FIXME: need a trip resemblance check to supplement the ui_activeItinerary param used in this function.
                ItineraryUtils.writeVerifiedItinerary(monitoredTrip, itineraries);
            }
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            // TODO: Bugsnag
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        ItineraryExistenceChecker.Result checkResult = checkItineraryExistence(monitoredTrip, req);

        // Replace the provided trip's itinerary with a verified, non-real-time version of it.
        writeVerifiedItinerary(monitoredTrip, req, checkResult.responses);
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
     * Checks that non-realtime itineraries exist for the days the specified monitored trip is active.
     */
    private static ItineraryExistenceChecker.Result checkItineraryExistence(MonitoredTrip trip, Request request) {
        ItineraryExistenceChecker itineraryChecker = new ItineraryExistenceChecker(OtpDispatcher::sendOtpPlanRequest);
        try {
            ItineraryExistenceChecker.Result checkResult = itineraryChecker.checkAll(ItineraryUtils.getItineraryExistenceQueries(trip));
            if (!checkResult.allItinerariesExist) {
                logMessageAndHalt(
                    request,
                    HttpStatus.BAD_REQUEST_400,
                    "An itinerary does not exist for some of the monitored days for the requested trip."
                );
            }
            return checkResult;
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            // TODO: Bugsnag
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
        return null;
    }
}
