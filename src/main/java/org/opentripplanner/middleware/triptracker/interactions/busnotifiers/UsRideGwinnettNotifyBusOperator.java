package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.isBusLeg;

/**
 * If conditions are correct, notify a bus operator of a traveler waiting to board at a given stop.
 */
public class UsRideGwinnettNotifyBusOperator implements BusOperatorInteraction {

    public static boolean IS_TEST = false;

    private static final Logger LOG = LoggerFactory.getLogger(UsRideGwinnettNotifyBusOperator.class);

    private static final String US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_URL
        = getConfigPropertyAsText("US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_URL", "not-provided");

    private static final String US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_KEY
        = getConfigPropertyAsText("US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_KEY", "not-provided");

    private static final String US_RIDE_GWINNETT_BUS_PRIORITY_API_URL
        = getConfigPropertyAsText("US_RIDE_GWINNETT_BUS_PRIORITY_API_URL", "not-provided");

    private static final String US_RIDE_GWINNETT_BUS_PRIORITY_API_KEY
        = getConfigPropertyAsText("US_RIDE_GWINNETT_BUS_PRIORITY_API_KEY", "not-provided");

    public static List<String> US_RIDE_GWINNETT_QUALIFYING_BUS_NOTIFIER_ROUTES = getBusOperatorNotifierQualifyingRoutes();

    /**
     * Headers that are required for bus driver notifications.
     */
    private static final Map<String, String> BUS_OPERATOR_NOTIFIER_API_HEADERS = Map.of(
        "Ocp-Apim-Subscription-Key", US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_KEY,
        "Content-Type", "application/json"
    );

    /**
     * Headers that are required for bus priority requests.
     */
    private static final Map<String, String> BUS_PRIORITY_API_HEADERS = Map.of(
        "x-api-key", US_RIDE_GWINNETT_BUS_PRIORITY_API_KEY,
        "Content-Type", "application/json"
    );


    /**
     * Get the routes which qualify for bus operator notifying from the configuration. The configuration value is
     * expected to be a comma separate list of agency id (as provided by OTP not agency) and route id e.g.
     * <p>
     * GwinnettCountyTransit:360,GwinnettCountyTransit:40,GwinnettCountyTransit:25
     */
    private static List<String> getBusOperatorNotifierQualifyingRoutes() {
        String busOperatorNotifierQualifyingRoutes
            = getConfigPropertyAsText("US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_QUALIFYING_ROUTES");
        if (busOperatorNotifierQualifyingRoutes != null) {
            return Arrays.asList(busOperatorNotifierQualifyingRoutes.split(","));
        }
        return new ArrayList<>();
    }

    /**
     * Stage notification to bus operator by making sure all required conditions are met.
     */
    public void sendNotification(TripStatus tripStatus, TravelerPosition travelerPosition) {
        var routeId = getRouteIdFromLeg(travelerPosition.nextLeg);
        try {
            if (
                hasNotSentNotificationForRoute(travelerPosition.trackedJourney, routeId) &&
                supportsBusOperatorNotification(routeId)
            ) {
                // Immediately set the notification state to pending, so that subsequent calls don't initiate another
                // request before this one completes.
                travelerPosition.trackedJourney.updateNotificationMessage(routeId, "pending");
                var body = createPostBody(travelerPosition);
                makeApiRequests(travelerPosition, body, routeId, "initiate");
            }
        } catch (Exception e) {
            LOG.error("Could not initiate Ride Gwinnett notification to bus operator.", e);
        }
    }

    /**
     * Cancel a previously sent notification for the next bus leg.
     */
    public void cancelNotification(TravelerPosition travelerPosition) {
        var routeId = getRouteIdFromLeg(travelerPosition.nextLeg);
        try {
            if (
                isBusLeg(travelerPosition.nextLeg) && routeId != null &&
                hasNotCanceledNotificationForRoute(travelerPosition.trackedJourney, routeId)
            ) {
                Map<String, String> busNotificationRequests = travelerPosition.trackedJourney.busNotificationMessages;
                if (busNotificationRequests.containsKey(routeId)) {
                    UsRideGwinnettBusOpNotificationMessage body = JsonUtils.getPOJOFromJSON(
                        busNotificationRequests.get(routeId),
                        UsRideGwinnettBusOpNotificationMessage.class
                    );
                    // Changed the saved message type from notify to cancel.
                    body.msg_type = 0;
                    String bodyJson = JsonUtils.toJson(body);
                    makeApiRequests(travelerPosition, bodyJson, routeId, "cancel");
                }
            }
        } catch (Exception e) {
            LOG.error("Could not cancel RideGwinnett notification to bus operator.", e);
        }
    }

    /**
     * Makes a call to the bus driver notification API, followed by a call to the bus priority API.
     */
    private static void makeApiRequests(TravelerPosition travelerPosition, String body, String routeId, String action) {
        var httpStatus = postBusDriverNotification(body);
        if (httpStatus == HttpStatus.OK_200) {
            travelerPosition.trackedJourney.updateNotificationMessage(routeId, body);
        } else {
            LOG.error("Error {} while trying to {} RideGwinnett notification to bus operator.", httpStatus, action);
        }

        // The same body is sent to the bus priority API.
        httpStatus = postBusPriorityNotification(body);
        if (httpStatus != HttpStatus.OK_200) {
            LOG.error("Error {} while trying to {} RideGwinnett bus priority request.", httpStatus, action);
        }
    }

    /**
     * Send bus driver notification and provide response. The service only provides the HTTP status as a response.
     */
    public static int postBusDriverNotification(String body) {
        if (IS_TEST) {
            return HttpStatus.OK_200;
        }
        var httpResponse = HttpUtils.httpRequestRawResponse(
            URI.create(US_RIDE_GWINNETT_BUS_OPERATOR_NOTIFIER_API_URL),
            1000,
            HttpMethod.POST,
            BUS_OPERATOR_NOTIFIER_API_HEADERS,
            body
        );
        return httpResponse.status;
    }

    /**
     * Send bus priority request and provide response. The service only provides the HTTP status as a response.
     */
    public static int postBusPriorityNotification(String body) {
        if (IS_TEST) {
            return HttpStatus.OK_200;
        }
        var httpResponse = HttpUtils.httpRequestRawResponse(
            URI.create(US_RIDE_GWINNETT_BUS_PRIORITY_API_URL),
            1000,
            HttpMethod.POST,
            BUS_PRIORITY_API_HEADERS,
            body
        );
        return httpResponse.status;
    }

    /**
     * Create post body that will be sent to bus notification API.
     */
    public static String createPostBody(TravelerPosition travelerPosition) {
        return JsonUtils.toJson(new UsRideGwinnettBusOpNotificationMessage(travelerPosition.currentTime, travelerPosition));
    }

    /**
     * Make sure the bus route associated with this leg supports notifying the bus operator. The 'gtfsId' is expected in
     * the format agency_id:route_id e.g. GwinnettCountyTransit:360. If no routes are defined it is assumed that all
     * routes support notification.
     */
    public static boolean supportsBusOperatorNotification(String routeId) {
        return
            US_RIDE_GWINNETT_QUALIFYING_BUS_NOTIFIER_ROUTES.isEmpty() ||
            US_RIDE_GWINNETT_QUALIFYING_BUS_NOTIFIER_ROUTES.contains(routeId);
    }

    /**
     * Has the bus driver already been notified or in the process of being notified for this journey.
     * The driver must only be notified once.
     */
    public static boolean hasNotSentNotificationForRoute(TrackedJourney trackedJourney, String routeId) {
        return !trackedJourney.busNotificationMessages.containsKey(routeId);
    }

    /**
     * Has a previous notification already been cancelled.
     */
    public static boolean hasNotCanceledNotificationForRoute(
        TrackedJourney trackedJourney,
        String routeId
    ) throws JsonProcessingException {
        String messageBody = trackedJourney.busNotificationMessages.get(routeId);
        if (messageBody == null) {
            throw new IllegalStateException("A notification must exist before it can be cancelled!");
        }
        UsRideGwinnettBusOpNotificationMessage message = getNotificationMessage(messageBody);
        return message.msg_type != 0;
    }

    public static UsRideGwinnettBusOpNotificationMessage getNotificationMessage(String body) throws JsonProcessingException {
        return JsonUtils.getPOJOFromJSON(body, UsRideGwinnettBusOpNotificationMessage.class);
    }
}