package org.opentripplanner.middleware.triptracker;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.triptracker.TripStatus.getSegmentStartTime;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * If conditions are correct notify a bus operator of a traveler joining the service at a given stop.
 */
public class NotifyBusOperator {

    public NotifyBusOperator() {}

    public static boolean IS_TEST = false;

    private static final Logger LOG = LoggerFactory.getLogger(NotifyBusOperator.class);

    private static final String BUS_OPERATOR_NOTIFIER_API_URL
        = getConfigPropertyAsText("BUS_OPERATOR_NOTIFIER_API_URL", "not-provided");

    private static final String BUS_OPERATOR_NOTIFIER_API_SUBSCRIPTION_KEY
        = getConfigPropertyAsText("BUS_OPERATOR_NOTIFIER_API_SUBSCRIPTION_KEY", "not-provided");

    public static List<String> QUALIFYING_BUS_NOTIFIER_ROUTES = getBusOperatorNotifierQualifyingRoutes();

    public static final int ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES = 15;

    /**
     * Headers that are required for each request.
     */
    private static final Map<String, String> BUS_OPERATOR_NOTIFIER_API_HEADERS = Map.of(
        "Ocp-Apim-Subscription-Key", BUS_OPERATOR_NOTIFIER_API_SUBSCRIPTION_KEY,
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
            = getConfigPropertyAsText("BUS_OPERATOR_NOTIFIER_QUALIFYING_ROUTES");
        if (busOperatorNotifierQualifyingRoutes != null) {
            return Arrays.asList(busOperatorNotifierQualifyingRoutes.split(","));
        }
        return new ArrayList<>();
    }

    /**
     * Stage notification to bus operator by making sure all required conditions are met.
     */
    public static void sendNotification(TripStatus tripStatus, TravelerPosition travelerPosition) {
        try {
            if (
                isBusLeg(travelerPosition.nextLeg) &&
                isWithinOperationalNotifyWindow(tripStatus, travelerPosition) &&
                hasNotPreviouslyNotifiedBusDriverForRoute(travelerPosition.trackedJourney, travelerPosition.nextLeg.route.id) &&
                supportsBusOperatorNotification(travelerPosition.nextLeg.route.id)
            ) {
                var body = createPostBody(travelerPosition);
                var httpStatus = doPost(body);
                if (httpStatus == HttpStatus.OK_200) {
                    travelerPosition.trackedJourney.updateNotificationMessage(travelerPosition.nextLeg.route.id, body);
                } else {
                    LOG.error("Error {} while trying to initiate notification to bus operator.", httpStatus);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not initiate notification to bus operator.", e);
        }
    }

    /**
     * Cancel a previously sent notification for the next bus leg.
     */
    public static void cancelNotification(TravelerPosition travelerPosition) {
        try {
            if (isBusLeg(travelerPosition.nextLeg) && travelerPosition.nextLeg.route.id != null) {
                String routeId = travelerPosition.nextLeg.route.id;
                Map<String, String> busNotificationRequests = travelerPosition.trackedJourney.busNotificationMessages;
                if (busNotificationRequests.containsKey(routeId)) {
                    BusOpNotificationMessage busOpNotificationMessage = JsonUtils.getPOJOFromJSON(
                        busNotificationRequests.get(routeId),
                        BusOpNotificationMessage.class
                    );
                    // Changed the saved message type from notify to cancel.
                    busOpNotificationMessage.msg_type = 0;
                    var httpStatus = doPost(JsonUtils.toJson(busOpNotificationMessage));
                    if (httpStatus == HttpStatus.OK_200) {
                        travelerPosition.trackedJourney.removeNotificationMessage(routeId);
                    } else {
                        LOG.error("Error {} while trying to cancel notification to bus operator.", httpStatus);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Could not cancel notification to bus operator.", e);
        }
    }

    /**
     * Send notification and provide response. The service only provides the HTTP status as a response.
     */
    public static int doPost(String body) {
        if (IS_TEST) {
            // TODO figure out how to mock a static method!
            return 200;
        }
        var httpResponse = HttpUtils.httpRequestRawResponse(
            URI.create(BUS_OPERATOR_NOTIFIER_API_URL),
            1000,
            HttpMethod.POST,
            BUS_OPERATOR_NOTIFIER_API_HEADERS,
            body
        );
        return httpResponse.status;
    }

    /**
     * Create post body that will be sent to bus notification API.
     */
    public static String createPostBody(TravelerPosition travelerPosition) {
        return JsonUtils.toJson(new BusOpNotificationMessage(travelerPosition.currentTime, travelerPosition));
    }

    /**
     * Make sure the leg in question is a bus transit leg.
     */
    public static boolean isBusLeg(Leg leg) {
        return leg != null && leg.mode.equalsIgnoreCase("BUS") && leg.transitLeg;
    }

    /**
     * Make sure the bus route associated with this leg supports notifying the bus operator. The 'gtfsId' is expected in
     * the format agency_id:route_id e.g. GwinnettCountyTransit:360.
     */
    public static boolean supportsBusOperatorNotification(String gtfsId) {
        return QUALIFYING_BUS_NOTIFIER_ROUTES.contains(gtfsId);
    }

    /**
     * Has the bus driver already been notified for this journey. The driver must only be notified once.
     */
    public static boolean hasNotPreviouslyNotifiedBusDriverForRoute(TrackedJourney trackedJourney, String routeId) {
        for (String notifiedRouteId : trackedJourney.busNotificationMessages.keySet()) {
            if (notifiedRouteId.equalsIgnoreCase(routeId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Make sure the traveler is on schedule or ahead of schedule (but not too far) to be within an operational window
     * for the bus service.
     */
    public static boolean isWithinOperationalNotifyWindow(TripStatus tripStatus, TravelerPosition travelerPosition) {
        return
            tripStatus.equals(TripStatus.ON_SCHEDULE) ||
            (
                tripStatus.equals(TripStatus.AHEAD_OF_SCHEDULE) &&
                ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES >= getMinutesAheadOfSchedule(travelerPosition)
            );
    }

    /**
     * Get how far ahead in minutes the traveler is from the expected schedule.
     */
    public static long getMinutesAheadOfSchedule(TravelerPosition travelerPosition) {
        Instant segmentStartTime = travelerPosition
            .expectedLeg
            .startTime
            .toInstant()
            .plusSeconds((long) getSegmentStartTime(travelerPosition.legSegmentFromPosition));
        return Duration.between(segmentStartTime, travelerPosition.currentTime).toMinutes();
    }
}