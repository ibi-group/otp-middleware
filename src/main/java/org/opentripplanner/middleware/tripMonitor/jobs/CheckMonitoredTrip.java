package org.opentripplanner.middleware.tripMonitor.jobs;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.tripMonitor.jobs.NotificationType.ARRIVAL_DELAY;
import static org.opentripplanner.middleware.tripMonitor.jobs.NotificationType.DEPARTURE_DELAY;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * This job handles the primary functions for checking a {@link MonitoredTrip}, including:
 * - determining if a check should be run (based on mostly date/time),
 * - making requests to OTP and comparing the stored itinerary against these new responses from OTP, and
 * - determining if notifications should be sent to the user monitoring the trip based on their saved criteria.
 */
public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN);

    private final MonitoredTrip trip;
    public int departureDelay;
    public int arrivalDelay;
    /**
     * Used to track the various check trip notifications and construct email/SMS messages.
     */
    public final Set<TripMonitorNotification> notifications = new HashSet<>();
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

    public String targetDate;

    public CheckMonitoredTrip(MonitoredTrip trip) {
        this.trip = trip;
    }

    @Override
    public void run() {
        // Check if the trip check should be skipped (based on time, day of week, etc.)
        try {
            if (shouldSkipMonitoredTripCheck()) {
                LOG.debug("Skipping check for trip: {}", trip.id);
                return;
            }
        } catch (Exception e) {
            // TODO: report to bugsnag
            LOG.error("Encountered an error while checking the monitored trip. error={}", e);
            return;
        }

        // Make a request to OTP with the monitored trip params if not already done in shouldSkipMonitoredTripCheck
        if (otpResponse == null && !calculateOtpResponse()) {
            // failed to calculate the OTP response, immediately end this job
            return;
        }

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
                success = NotificationUtils.sendEmailViaSparkpost(otpUser.email, subject, body.toString(), null);
                break;
            case "all":
                // TOOD better handle below when one of the following fails
                success = NotificationUtils.sendSMS(otpUser.phoneNumber, body.toString()) != null &&
                    NotificationUtils.sendEmailViaSparkpost(otpUser.email, subject, body.toString(), null);
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
     * Determine whether to skip checking the monitored trip at this instant. The decision on whether to skip the check
     * takes into account the current time, the lead time prior to the itinerary start and the last time that the trip
     * was checked. Skipping the check should only occur if the previous trip has ended and the next trip meets the
     * following criteria for skipping a check:
     *
     * - the current time is before when the lead time before the next itinerary starts
     * - the current time is after the lead time before the next itinerary starts, but is over an hour until the
     *     itinerary start time and the trip has already been checked within the last 60 minutes
     * - the current time is after the lead time before the next itinerary starts and between 60-15 minutes prior to the
     *     itinerary start time, but a check has occurred within the last 15 minutes
     *
     * These checks are done based off of the information in the trip's journey state's latest itinerary. If no such
     * itinerary exists or a previous monitored trip's itinerary has completed, then the next possible itinerary will be
     * calculated and updated in the monitored trip's journey state.
     */
    public boolean shouldSkipMonitoredTripCheck() throws Exception {
        // before anything else, return true if the trip is inactive
        if (trip.isInActive()) return true;

        // calculate the appropriate timezone to use for the target time based off of the appropriate trip end location
        ZoneId targetZoneId = trip.getTimezoneForTargetLocation();

        // get the most recent journey state and itinerary to see when the next monitored trip is supposed to occur
        JourneyState latestJourneyState = trip.retrieveJourneyState();
        Itinerary latestItinerary = trip.latestItinerary();
        if (latestItinerary == null || latestItinerary.endTime.before(latestJourneyState.lastUpdated)) {
            // Either the monitored trip hasn't ever checked on the next itinerary, or the most recent itinerary has
            // completed and the next possible one needs to be fetched in order to determine the scheduled start time of
            // the itinerary on the next possible day the monitored trip happens

            // calculate target time for the next trip plan request
            // find the next possible day the trip is active by initializing the the appropriate target time on today's
            // date
            ZonedDateTime targetZonedDateTime = DateTimeUtils.nowAsZonedDateTime(targetZoneId)
                .withHour(trip.getHour())
                .withMinute(trip.getMinute())
                .withSecond(0);

            // if the trip is not active on the current zoned date time, advance until a day is found when the trip is
            // active. It is guaranteed that the trip will be active on a certain date because a call to
            // {@link MonitoredTrip#isInactive} has already been made.
            while (!trip.isActiveOnDate(targetZonedDateTime)) {
                targetZonedDateTime = targetZonedDateTime.plusDays(1);
            }
            targetDate = targetZonedDateTime.format(DATE_FORMATTER);

            // execute trip plan request for the target time
            if (!calculateOtpResponse()) {
                // failed to calculate, skip the trip check
                return true;
            }

            // check if the
            // save response in journey state
            currentItineraryStartTime = trip.nextAnticipatedItineraryStartTime();
        } else {
            targetDate = latestJourneyState.targetDate;
        }
        Instant currentItineraryStartTime = Instant.ofEpochSecond(latestItinerary.startTime.getTime());

        // Get current time and trip time (with the time offset to today) for comparison.
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime(zoneId);
        // TODO: Determine whether we want to monitor trips during the length of the trip.
        //  If we do this, we will want to use the itinerary end time (and destination zoneId) to determine this time
        //  value. Also, we may want to use the start time leading up to the trip (for periodic monitoring) and once
        //  the trip has started, switch to some other monitoring interval while the trip is in progress.
        ZonedDateTime tripTime = ZonedDateTime.ofInstant(trip.itinerary.startTime.toInstant(), zoneId)
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
        LOG.info("{} minutes since last check", minutesSinceLastCheck);
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

    private boolean calculateOtpResponse() {
        OtpDispatcherResponse otpDispatcherResponse = null;
        try {
            otpDispatcherResponse = OtpDispatcher.sendOtpPlanRequest(generateTripPlanQueryParams());
        } catch (Exception e) {
            // TODO: report bugsnag
            LOG.error("Encountered an error while making a request ot the OTP server. error={}", e);
            return false;
        }

        if (otpDispatcherResponse.statusCode >= 400) {
            // TODO: report bugsnag
            LOG.error("Received an error from the OTP server. status={}", otpDispatcherResponse.statusCode);
            return false;
        }
        otpResponse = otpDispatcherResponse.getResponse();
        return true;
    }

    /**
     * Generate the appropriate OTP query params for the trip for the current check by replacing
     * the date query parameter with the appropriate date.
     */
    public String generateTripPlanQueryParams() throws URISyntaxException {
        // parse query params
        List<NameValuePair> params = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", trip.queryParams)),
            UTF_8
        );

        // building a new list by copying all values, except for the date which is set to the target date
        List<NameValuePair> newParams = new ArrayList<>();
        for (NameValuePair param : params) {
            if (param.getName().equals("date")) {
                newParams.add(new BasicNameValuePair("date", targetDate));
            } else { newParams.add(param); }
        }

        // convert object to string
        return URLEncodedUtils.format(newParams, UTF_8);
    }
}
