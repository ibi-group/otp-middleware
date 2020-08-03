package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.DateUtils.getZoneIdForCoordinates;

/**
 * This job handles the primary functions for checking a {@link MonitoredTrip}, including:
 * - determining if a check should be run (based on mostly date/time),
 * - making requests to OTP and comparing the stored itinerary against these new responses from OTP, and
 * - determining if notifications should be sent to the user monitoring the trip based on their saved criteria.
 */
public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
    private final MonitoredTrip trip;
    /**
     * This is only used during testing to inject a mock OTP response for comparison against a monitored trip.
     */
    private OtpDispatcherResponse injectedOtpResponseForTesting;
    private final List<TripMonitorNotification> notifications = new ArrayList<>();

    public CheckMonitoredTrip(MonitoredTrip trip) {
        this.trip = trip;
    }

    public CheckMonitoredTrip(MonitoredTrip trip, OtpDispatcherResponse injectedOtpResponseForTesting) {
        this.trip = trip;
        this.injectedOtpResponseForTesting = injectedOtpResponseForTesting;
    }

    @Override
    public void run() {
        // Check if the trip check should be skipped (based on time, day of week, etc.)
        if (shouldSkipMonitoredTripCheck(trip)) {
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
        Response otpResponse = otpDispatcherResponse.getResponse();
        // TODO: Should null tripRequestId be fixed?
        // TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan, otpDispatcherResponse.response.error, null);
        // TODO: BIG - Actually find the specific itinerary to compare against. For now, just choose the first itin.
        Itinerary itinerary = findComparisonItinerary(trip, otpResponse);
        // BEGIN CHECKS
        // Check for notifications related to service alerts.
        checkTripForNewAlerts(trip, itinerary);
        // Check for notifications related to delays
        checkTripForDelays(trip, itinerary);
        // Check for notifications related to itinerary changes
        checkTripForItineraryChanges(trip, itinerary);
        // Add latest OTP response.
        trip.addResponse(otpResponse);
        Persistence.monitoredTrips.replace(trip.id, trip);
        // Send notification to user.
        sendNotification();
    }

    private Itinerary findComparisonItinerary(MonitoredTrip trip, Response otpResponse) {
        // TODO: BIG - Find the specific itinerary to compare against. For now, just choose the first itin.
        return otpResponse.plan.itineraries.get(0);
    }

    private void checkTripForNewAlerts(MonitoredTrip trip, Itinerary itinerary) {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip {}. Skipping check.", trip.id);
            return;
        }
        // FIXME: Need to somehow determine how to get last itinerary from responses in journey state.
        Itinerary latestItinerary = trip.latestItinerary();
        // Construct set from new alerts.
        Set<LocalizedAlert> newAlerts = new HashSet<>(itinerary.getAlerts());
        Set<LocalizedAlert> previousAlerts = new HashSet<>(latestItinerary.getAlerts());
        // Unseen alerts consists of all new alerts that we did not previously track.
        Set<LocalizedAlert> unseenAlerts = new HashSet<>(newAlerts);
        unseenAlerts.addAll(previousAlerts);
        // Resolved alerts consists of all previous alerts that no longer exist.
        Set<LocalizedAlert> resolvedAlerts = new HashSet<>(previousAlerts);
        resolvedAlerts.removeAll(newAlerts);
        // If journey state is already tracking alerts from previous checks, see if they have changed.
        if (unseenAlerts.size() > 0 || resolvedAlerts.size() > 0) {
            enqueueNotification(TripMonitorNotification.createAlertNotification(unseenAlerts, resolvedAlerts));
        }
    }

    private void checkTripForDelays(MonitoredTrip trip, Itinerary itinerary) {
        // TODO
    }

    private void checkTripForItineraryChanges(MonitoredTrip trip, Itinerary itinerary) {
        // TODO
    }

    private void sendNotification() {
        if (notifications.size() == 0) {
            // FIXME: Change log level
            LOG.info("No notifications queued up. Skipping notify.");
            return;
        }
        OtpUser otpUser = Persistence.otpUsers.getById(trip.userId);
        if (otpUser == null) {
            LOG.error("Cannot find user for id {}", trip.userId);
            // TODO: Bugsnag / delete monitored trip?
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
        // FIXME: This needs to be an enum.
        switch (otpUser.notificationChannel.toLowerCase()) {
            // TODO: Use medium-specific messages (i.e., SMS body should be shorter/phone friendly)
            case "sms":
                NotificationUtils.sendSMS(otpUser.phoneNumber, body.toString());
                break;
            case "email":
                NotificationUtils.sendEmail(otpUser.email, subject, body.toString(), null);
                break;
            case "all":
                NotificationUtils.sendSMS(otpUser.phoneNumber, body.toString());
                NotificationUtils.sendEmail(otpUser.email, subject, body.toString(), null);
                break;
            default:
                break;
        }
    }

    private void enqueueNotification(TripMonitorNotification tripMonitorNotification) {
        notifications.add(tripMonitorNotification);
    }

    private boolean shouldSkipMonitoredTripCheck(MonitoredTrip trip) {
        // Default to system's zone if one can't be inferred from trip destination.
        ZoneId zoneId = ZoneId.systemDefault();
        // TODO: Verify that this check doesn't cost anything.
        Optional<ZoneId> zoneIdForCoordinates = getZoneIdForCoordinates(trip.to.lat, trip.to.lon);
        if (zoneIdForCoordinates.isEmpty()) {
            LOG.error("Could not find timezone for monitored trip: {}", trip.id);
        } else {
            zoneId = zoneIdForCoordinates.get();
        }
        // Get current time and trip time for comparison.
        LocalDate now = LocalDate.now(zoneId);
        LocalDate tripTime = trip.itinerary.startTime.toInstant()
            .atZone(zoneId)
            .toLocalDate();
        // Skip check if trip is not active today or trip has already occurred.
        if (!trip.isActiveOnDate(now) || tripTime.isAfter(now)) return true;
        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long millisSinceLastCheck = System.currentTimeMillis() - trip.retrieveJourneyState().lastChecked;
        long minutesSinceLastCheck = TimeUnit.MILLISECONDS.toMinutes(millisSinceLastCheck);
        long minutesUntilTrip = Duration.between(tripTime.atStartOfDay(), now.atStartOfDay()).toMinutes();
        // TODO: Refine these frequency intervals for monitor checks.
        // If time until trip is greater than 60 minutes, we only need to check once every hour.
        if (minutesUntilTrip > 60) {
            // It's been about an hour since the last check. Do not skip.
            if (minutesSinceLastCheck <= 60) {
                return false;
            }
        } else {
            // It's less than an hour until the trip time, start more frequent trip checks (about every 15 minutes).
            if (minutesSinceLastCheck <= 15) {
                return false;
            }
            // During the last 5 minutes, check every minute (assuming the loop runs every minute).
            if (minutesUntilTrip <= 5) {
                return false;
            }
        }
        // TODO: Check that journey state is not flagged?
        // Default to skipping check.
        return true;
    }
}
