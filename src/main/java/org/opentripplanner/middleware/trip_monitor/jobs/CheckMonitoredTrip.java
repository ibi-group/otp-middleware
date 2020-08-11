package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.trip_monitor.jobs.NotificationType.ARRIVAL_DELAY;
import static org.opentripplanner.middleware.trip_monitor.jobs.NotificationType.DEPARTURE_DELAY;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getZoneIdForCoordinates;

/**
 * This job handles the primary functions for checking a {@link MonitoredTrip}, including:
 * - determining if a check should be run (based on mostly date/time),
 * - making requests to OTP and comparing the stored itinerary against these new responses from OTP, and
 * - determining if notifications should be sent to the user monitoring the trip based on their saved criteria.
 */
public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
    private final MonitoredTrip trip;
    public int departureDelay;
    public int arrivalDelay;
    /**
     * This is only used during testing to inject a mock OTP response for comparison against a monitored trip.
     */
    private OtpDispatcherResponse injectedOtpResponseForTesting;
    /**
     * Used to track the various check trip notifications and construct email/SMS messages.
     */
    public final Set<TripMonitorNotification> notifications = new HashSet<>();
    /**
     * Whether the check was skipped based on time/date criteria.
     */
    public boolean checkSkipped;
    /**
     * The index of the matching {@link Itinerary} as found in the OTP {@link Response} planned as part of this check.
     */
    public int matchingItineraryIndex = -1;
    /**
     * The OTP response planned to check the stored {@link Itinerary} against.
     */
    public Response otpResponse;
    /**
     * Tracks the time the notification was sent to the user.
     */
    public long notificationTimestamp = -1;

    public CheckMonitoredTrip(MonitoredTrip trip) {
        this.trip = trip;
    }

    /**
     * This constructor should only be used for testing when we need to inject a mock OTP response.
     */
    public CheckMonitoredTrip(MonitoredTrip trip, OtpDispatcherResponse injectedOtpResponseForTesting) {
        this.trip = trip;
        this.injectedOtpResponseForTesting = injectedOtpResponseForTesting;
    }

    @Override
    public void run() {
        // Check if the trip check should be skipped (based on time, day of week, etc.)
        checkSkipped = shouldSkipMonitoredTripCheck(trip);
        if (checkSkipped) {
            LOG.debug("Skipping check for trip: {}", trip.id);
            return;
        }
        // Make a request to OTP with the monitored trip params or if this is in a test environment use the injected OTP
        // response.
        OtpDispatcherResponse otpDispatcherResponse = injectedOtpResponseForTesting != null
            ? injectedOtpResponseForTesting
            : OtpDispatcher.sendOtpPlanRequest(trip.queryParams);

        if (otpDispatcherResponse.statusCode >= 400) {
            // TODO: report bugsnag
            LOG.error("Could not reach OTP server. status={}", otpDispatcherResponse.statusCode);
            return;
        }
        otpResponse = otpDispatcherResponse.getResponse();
        // Check monitored trip.
        runCheckLogic();
        // Send notifications to user. This should happen before updating the journey state so that we can check the
        // last notification sent.
        sendNotifications();
        // Update journey state.
        JourneyState journeyState = trip.retrieveJourneyState();
        journeyState.update(this);
    }

    private void runCheckLogic() {
        // TODO: Should null tripRequestId be fixed?
        // TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan, otpDispatcherResponse.response.error, null);
        matchingItineraryIndex = findMatchingItineraryIndex(trip, otpResponse);
        if (matchingItineraryIndex == -1) {
            // No matching itinerary was found.
            enqueueNotification(TripMonitorNotification.createItineraryNotFoundNotification());
            // TODO: Try some fancy things to construct a matching itinerary (e.g., transit index?).
            return;
        }
        // Matching itinerary found in OTP response. Run real-time checks.
        Itinerary itinerary = otpResponse.plan.itineraries.get(matchingItineraryIndex);
        enqueueNotification(
            // Check for notifications related to service alerts.
            checkTripForNewAlerts(trip, itinerary),
            // Check for notifications related to delays.
            checkTripForDepartureDelay(trip, itinerary),
            checkTripForArrivalDelay(trip, itinerary)
        );
    }

    /**
     * Find an itinerary from the OTP response that matches the monitored trip's stored itinerary.
     */
    private static int findMatchingItineraryIndex(MonitoredTrip trip, Response otpResponse) {
        for (int i = 0; i < otpResponse.plan.itineraries.size(); i++) {
            // TODO: BIG - Find the specific itinerary to compare against. For now, use equals, but this may need some
            //  tweaking
            Itinerary candidateItinerary = otpResponse.plan.itineraries.get(i);
            if (candidateItinerary.equals(trip.itinerary)) return i;
        }
        LOG.warn("No comparison itinerary found in otp response for trip {}", trip.id);
        return -1;
    }

    public static TripMonitorNotification checkTripForNewAlerts(MonitoredTrip trip, Itinerary itinerary) {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip {}. Skipping check.", trip.id);
            return null;
        }
        // Get the previously checked itinerary/alerts from the journey state (i.e., the response from OTP the most
        // recent the trip check was run). If no check has yet been run, this will be null.
        Itinerary latestItinerary = trip.latestItinerary();
        Set<LocalizedAlert> previousAlerts = latestItinerary == null
            ? Collections.EMPTY_SET
            : new HashSet<>(latestItinerary.getAlerts());
        // Construct set from new alerts.
        Set<LocalizedAlert> newAlerts = new HashSet<>(itinerary.getAlerts());
        TripMonitorNotification notification = TripMonitorNotification.createAlertNotification(previousAlerts, newAlerts);
        if (notification == null) {
            // TODO: Change log level
            LOG.info("No unseen/resolved alerts found for trip {}", trip.id);
        }
        return notification;
    }

    public static TripMonitorNotification checkTripForDepartureDelay(MonitoredTrip trip, Itinerary itinerary) {
        long departureDelayInMinutes = TimeUnit.SECONDS.toMinutes(itinerary.legs.get(0).departureDelay);
        // First leg departure time should not exceed variance allowed.
        if (departureDelayInMinutes >= trip.departureVarianceMinutesThreshold) {
            return TripMonitorNotification.createDelayNotification(departureDelayInMinutes, trip.departureVarianceMinutesThreshold, DEPARTURE_DELAY);
        }
        return null;
    }

    public static TripMonitorNotification checkTripForArrivalDelay(MonitoredTrip trip, Itinerary itinerary) {
        Leg lastLeg = itinerary.legs.get(itinerary.legs.size() - 1);
        long arrivalDelayInMinutes = TimeUnit.SECONDS.toMinutes(lastLeg.arrivalDelay);
        // Last leg arrival time should not exceed variance allowed.
        if (arrivalDelayInMinutes >= trip.arrivalVarianceMinutesThreshold) {
            return TripMonitorNotification.createDelayNotification(arrivalDelayInMinutes, trip.arrivalVarianceMinutesThreshold, ARRIVAL_DELAY);
        }
        return null;
    }

    /**
     * Compose a message for any enqueued notifications and send to {@link OtpUser} based on their notification
     * preferences.
     */
    private void sendNotifications() {
        if (notifications.size() == 0) {
            // FIXME: Change log level
            LOG.info("No notifications queued for trip {}. Skipping notify.", trip.id);
            return;
        }
        OtpUser otpUser = Persistence.otpUsers.getById(trip.userId);
        if (otpUser == null) {
            LOG.error("Cannot find user for id {}", trip.userId);
            // TODO: Bugsnag / delete monitored trip?
            return;
        }
        // If the same notifications were just sent, there is no need to send the same notification.
        // TODO: Should there be some time threshold check here based on lastNotificationTime?
        JourneyState journeyState = trip.retrieveJourneyState();
        if (journeyState.lastNotifications.containsAll(notifications)) {
            LOG.info("Trip {} last notifications match current ones. Skipping notify.", trip.id);
            return;
        }
        String name = trip.tripName != null ? trip.tripName : "Trip for " + otpUser.email;
        String subject = name + " Notification";
        StringBuilder body = new StringBuilder();
        for (TripMonitorNotification notification : notifications) {
            body.append(notification.body);
        }
        // FIXME: Change log level
        LOG.info("Sending notification '{}' to user {}", subject, trip.userId);
        boolean success = false;
        // FIXME: This needs to be an enum.
        switch (otpUser.notificationChannel.toLowerCase()) {
            // TODO: Use medium-specific messages (i.e., SMS body should be shorter/phone friendly)
            case "sms":
                success = NotificationUtils.sendSMS(otpUser.phoneNumber, body.toString()) != null;
                break;
            case "email":
                success = NotificationUtils.sendEmail(otpUser.email, subject, body.toString(), null);
                break;
            case "all":
                NotificationUtils.sendSMS(otpUser.phoneNumber, body.toString());
                NotificationUtils.sendEmail(otpUser.email, subject, body.toString(), null);
                break;
            default:
                break;
        }
        if (success) {
            notificationTimestamp = DateTimeUtils.currentTimeMillis();
        }
    }

    private void enqueueNotification(TripMonitorNotification ...tripMonitorNotifications) {
        for (TripMonitorNotification notification : tripMonitorNotifications) {
            if (notification != null) notifications.add(notification);
        }
    }

    /**
     * Check whether monitored trip check should be skipped.
     * TODO: Should this be a method on {@link MonitoredTrip}?
     */
    public static boolean shouldSkipMonitoredTripCheck(MonitoredTrip trip) {
        ZoneId zoneId;
        Optional<ZoneId> fromZoneId = getZoneIdForCoordinates(trip.from.lat, trip.from.lon);
        if (fromZoneId.isEmpty()) {
            String message = String.format(
                "Could not find coordinate's (lat=%.6f, lon=%.6f) timezone for monitored trip %s",
                trip.from.lat,
                trip.from.lon,
                trip.id
            );
            throw new RuntimeException(message);
        } else {
            zoneId = fromZoneId.get();
        }
        // Get current time and trip time (with the time offset to today) for comparison.
        LocalDateTime now = DateTimeUtils.nowAsLocalDateTime(zoneId);
        // TODO: Determine whether we want to monitor trips during the length of the trip.
        //  If we do this, we will want to use the itinerary end time (and destination zoneId) to determine this time
        //  value. Also, we may want to use the start time leading up to the trip (for periodic monitoring) and once
        //  the trip has started, switch to some other monitoring interval while the trip is in progress.
        LocalDateTime tripTime = trip.itinerary.startTime.toInstant()
            .atZone(zoneId)
            .toLocalDateTime()
            // Offset the trip time to today's date.
            .withYear(now.getYear())
            .withMonth(now.getMonthValue())
            .withDayOfMonth(now.getDayOfMonth());
        // TODO: Change log level.
        LOG.info("Trip {} starts at {} (now={})", trip.id, tripTime.toString(), now.toString());
        // TODO: This check may eventually make use of endTime and be used in conjunction with tripInProgress check (see
        //  above).
        boolean tripHasEnded = now.isAfter(tripTime);
        // Skip check if trip is not active today.
        if (!trip.isActiveOnDate(now)) {
            // TODO: Change log level.
            LOG.info("Trip {} not active today.", trip.id);
            return true;
        }
        // If the trip has already occurred, clear the journey state.
        // TODO: Decide whether this should happen at some other time.
        if (tripHasEnded) {
            // TODO: Change log level.
            LOG.info("Trip {} has cleared.", trip.id);
            // TODO: If we decide to stack up responses, we should clear them here.
//            trip.clearJourneyState();
            return true;
        }
        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long millisSinceLastCheck = DateTimeUtils.currentTimeMillis() - trip.retrieveJourneyState().lastChecked;
        long minutesSinceLastCheck = TimeUnit.MILLISECONDS.toMinutes(millisSinceLastCheck);
        long minutesUntilTrip = Duration.between(now, tripTime).toMinutes();
        LOG.info("Trip {} starts in {} minutes", trip.id, minutesUntilTrip);
        // TODO: Refine these frequency intervals for monitor checks.
        // If time until trip is greater than 60 minutes, we only need to check once every hour.
        if (minutesUntilTrip > 60) {
            // It's been about an hour since the last check. Do not skip.
            if (minutesSinceLastCheck >= 60) {
                // TODO: Change log level.
                LOG.info("Trip {} not checked in at least an hour. Checking.", trip.id);
                return false;
            }
        } else {
            // It's less than an hour until the trip time, start more frequent trip checks (about every 15 minutes).
            if (minutesSinceLastCheck >= 15) {
                // Last check was more than 15 minutes ago. Check. (approx. 4 checks per hour).
                // TODO: Change log level.
                LOG.info("Trip {} happening soon. Checking.", trip.id);
                return false;
            }
            // If the minutes until the trip is within the lead time, check the trip every minute
            // (assuming the loop runs every minute).
            // TODO: Should lead time just be used for notification time limit?
            if (minutesUntilTrip <= trip.leadTimeInMinutes) {
                // TODO: Change log level.
                LOG.info(
                    "Trip {} happening within user's lead time ({} minutes). Checking every minute.",
                    trip.id,
                    trip.leadTimeInMinutes
                );
                return false;
            }
        }
        // TODO: Check that journey state is not flagged
        // TODO: Check last notification time.
        // Default to skipping check.
        // TODO: Change log level.
        LOG.info("Trip {} criteria not met to check. Skipping.", trip.id);
        return true;
    }
}
