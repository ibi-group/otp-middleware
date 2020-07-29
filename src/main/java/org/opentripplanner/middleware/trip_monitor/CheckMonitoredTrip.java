package org.opentripplanner.middleware.trip_monitor;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.trip_monitor.jobs.TripMonitorNotification;
import org.opentripplanner.middleware.utils.DateUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
    private final MonitoredTrip trip;
    private OtpDispatcherResponse injectedOtpResponse;
    private final List<TripMonitorNotification> notifications = new ArrayList<>();

    public CheckMonitoredTrip(MonitoredTrip trip) {
        this.trip = trip;
    }

    public CheckMonitoredTrip(MonitoredTrip trip, OtpDispatcherResponse injectedOtpResponse) {
        this.trip = trip;
        this.injectedOtpResponse = injectedOtpResponse;
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
        OtpDispatcherResponse otpDispatcherResponse = injectedOtpResponse != null
            ? injectedOtpResponse
            : OtpDispatcher.sendOtpPlanRequest(trip.queryParams);

        if (otpDispatcherResponse.statusCode >= 400) {
            // TODO: report bugsnag
            LOG.error("Could not reach OTP server. status={}", otpDispatcherResponse.statusCode);
            return;
        }
        Response otpResponse = otpDispatcherResponse.getResponse();
        // TODO: Should null tripRequestId be fixed?
        // TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan, otpDispatcherResponse.response.error, null);
        // TODO: Find the specific itinerary to compare against. For now, just choose the first itin.
        Itinerary itinerary = otpResponse.plan.itineraries.get(0);
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

    private void checkTripForItineraryChanges(MonitoredTrip trip, Itinerary itinerary) {
        // TODO
    }

    private void checkTripForDelays(MonitoredTrip trip, Itinerary itinerary) {
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

    private void checkTripForNewAlerts(MonitoredTrip trip, Itinerary itinerary) {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip {}. Skipping check.", trip.id);
            return;
        }
        // FIXME: Need to somehow determine how to get last itinerary from responses in journey state.
        Itinerary lastItinerary = trip.lastItinerary();
        List<LocalizedAlert> newAlerts = itinerary.getAlerts();
        List<LocalizedAlert> previousAlerts = lastItinerary.getAlerts();
        // If journey state is already tracking alerts from previous checks, see if they have changed.
        if (previousAlerts.size() > 0) {
            // If there are new alerts, notify user.
            List<LocalizedAlert> unseenAlerts = new ArrayList<>();
            for (LocalizedAlert newAlert : newAlerts) {
                boolean alertIsNew = true;
                for (LocalizedAlert previousAlert : previousAlerts) {
                    if (newAlert.equals(previousAlert)) {
                        alertIsNew = false;
                        break;
                    }
                }
                if (alertIsNew) unseenAlerts.add(newAlert);
            }
            if (unseenAlerts.size() > 0) {
                // FIXME: May need to update the user about alerts that have dropped off as well.
                enqueueNotification(TripMonitorNotification.createAlertNotification(unseenAlerts));
            }
        } else {
            // If there are new alerts present in the response, create notification.
            if (newAlerts.size() > 0) {
                enqueueNotification(TripMonitorNotification.createAlertNotification(newAlerts));
            }
        }
    }

    private void enqueueNotification(TripMonitorNotification tripMonitorNotification) {
        notifications.add(tripMonitorNotification);
    }

    private boolean shouldSkipMonitoredTripCheck(MonitoredTrip trip) {
        LocalDate now = LocalDate.now();
        LocalDate tripTime;
        try {
            tripTime = DateUtils.getDateFromString(trip.tripTime, "HH:mm");
        } catch (DateTimeParseException e) {
            // TODO: Bugsnag report
            LOG.error("Could not parse trip time", e);
            return false;
        }
        // Trip is not active today.
        return !trip.isActiveOnDate(now) &&
            // Trip has already occurred.
            tripTime.isAfter(now);
        // TODO: Check that last comparison was X minutes ago?
        // TODO: Check that journey state is not flagged?
    }
}
