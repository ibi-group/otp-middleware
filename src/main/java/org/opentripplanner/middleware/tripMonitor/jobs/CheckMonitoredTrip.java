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
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
     * The index of the matching {@link Itinerary} as found in the OTP {@link OtpResponse} planned as part of this check.
     */
    public Itinerary matchingItinerary;
    /**
     * The OTP response planned to check the stored {@link Itinerary} against.
     */
    public OtpResponse otpResponse;
    /**
     * Tracks the time the notification was sent to the user.
     */
    public long notificationTimestampMillis = -1;

    public String targetDate;

    private ZonedDateTime targetZonedDateTime;

    private JourneyState journeyState;

    public CheckMonitoredTrip(MonitoredTrip trip) {
        this.trip = trip;
    }

    @Override
    public void run() {
        // Add a prefix of the current trip ID for logging purposes to every log message generated from within an
        // instance of this class. This assumes that the logback.xml file is properly configured to print this variable.
        // See http://logback.qos.ch/manual/mdc.html for more info.
        MDC.put("prefix", String.format("[Trip ID: %s]", trip.id));
        try {
            doRun();
        } finally {
            MDC.clear();
        }
    }
    
    private void doRun() {
        LOG.info("Begin checking trip.");
        // Check if the trip check should be skipped (based on time, day of week, etc.)
        try {
            if (shouldSkipMonitoredTripCheck()) {
                LOG.debug("Skipping check for trip");
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
        journeyState.update(this);
    }

    private void runCheckLogic() {
        // TODO: Should null tripRequestId be fixed?
        // TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan, otpDispatcherResponse.response.error, null);
        if (!findMatchingItinerary()) {
            // No matching itinerary was found.
            enqueueNotification(TripMonitorNotification.createItineraryNotFoundNotification());
            return;
        }
        // Matching itinerary found in OTP response. Run real-time checks.
        enqueueNotification(
            // Check for notifications related to service alerts.
            checkTripForNewAlerts(),
            // Check for notifications related to delays.
            checkTripForDepartureDelay(),
            checkTripForArrivalDelay()
        );
    }

    /**
     * Find and set the matching itinerary from the OTP response that matches the monitored trip's stored itinerary if a
     * match exists.
     *
     * FIXME: the itinerary might actually still be possible, but for some reason the OTP plan didn't find the same
     *          match. Some additional checks should be performed to make sure the itinerary really isn't possible by
     *          verifying that the same transit schedule/routes exist and that the street network is the same
     */
    private boolean findMatchingItinerary() {
        for (int i = 0; i < otpResponse.plan.itineraries.size(); i++) {
            // TODO: BIG - Find the specific itinerary to compare against. For now, use equals, but this may need some
            //  tweaking
            Itinerary candidateItinerary = otpResponse.plan.itineraries.get(i);
            if (candidateItinerary.equals(trip.itinerary)) {
                matchingItinerary = candidateItinerary;
                return true;
            }
        }
        LOG.warn("No comparison itinerary found in otp response for trip");
        return false;
    }

    public TripMonitorNotification checkTripForNewAlerts() {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip. Skipping check.");
            return null;
        }
        // Get the previously checked itinerary/alerts from the journey state (i.e., the response from OTP the most
        // recent the trip check was run). If no check has yet been run, this will be null.
        Itinerary latestItinerary = trip.latestItinerary();
        Set<LocalizedAlert> previousAlerts = latestItinerary == null
            ? Collections.EMPTY_SET
            : new HashSet<>(latestItinerary.getAlerts());
        // Construct set from new alerts.
        Set<LocalizedAlert> newAlerts = new HashSet<>(matchingItinerary.getAlerts());
        TripMonitorNotification notification = TripMonitorNotification.createAlertNotification(previousAlerts, newAlerts);
        if (notification == null) {
            // TODO: Change log level
            LOG.info("No unseen/resolved alerts found for trip.");
        }
        return notification;
    }

    public TripMonitorNotification checkTripForDepartureDelay() {
        long departureDelayInMinutes = TimeUnit.SECONDS.toMinutes(matchingItinerary.legs.get(0).departureDelay);
        // First leg departure time should not exceed variance allowed.
        if (departureDelayInMinutes >= trip.departureVarianceMinutesThreshold) {
            return TripMonitorNotification.createDelayNotification(departureDelayInMinutes, trip.departureVarianceMinutesThreshold, DEPARTURE_DELAY);
        }
        return null;
    }

    public TripMonitorNotification checkTripForArrivalDelay() {
        Leg lastLeg = matchingItinerary.legs.get(matchingItinerary.legs.size() - 1);
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
            LOG.info("No notifications queued for trip. Skipping notify.");
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
        if (journeyState.lastNotifications.containsAll(notifications)) {
            LOG.info("Last notifications match current ones. Skipping notify.");
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
            notificationTimestampMillis = DateTimeUtils.currentTimeMillis();
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
        journeyState = trip.retrieveJourneyState();
        if (
            journeyState.matchingItinerary == null ||
                journeyState.matchingItinerary.endTime.before(new Date(journeyState.lastCheckedMillis))
        ) {
            // Either the monitored trip hasn't ever checked on the next itinerary, or the most recent itinerary has
            // completed and the next possible one needs to be fetched in order to determine the scheduled start time of
            // the itinerary on the next possible day the monitored trip happens

            LOG.info("Calculating next itinerary for trip");
            // calculate target time for the next trip plan request
            // find the next possible day the trip is active by initializing the the appropriate target time. Start by
            // checking today's date at the earliest in case the user has paused trip monitoring for a while
            targetZonedDateTime = DateTimeUtils.nowAsZonedDateTime(targetZoneId)
                .withHour(trip.getHour())
                .withMinute(trip.getMinute())
                .withSecond(0);

            // if a previous journeyState target date exists, check if the previous target date was today's date. If so,
            // immediately advance to the next day
            if (journeyState.targetDate != null) {
                LocalDate lastDate = DateTimeUtils.getDateFromString(
                    journeyState.targetDate,
                    DEFAULT_DATE_FORMAT_PATTERN
                );
                if (
                    lastDate.getYear() == targetZonedDateTime.getYear() &&
                        lastDate.getMonthValue() == targetZonedDateTime.getMonthValue() &&
                        lastDate.getDayOfMonth() == targetZonedDateTime.getDayOfMonth()
                ) {
                    targetZonedDateTime = targetZonedDateTime.plusDays(1);
                }
            }

            // calculate the next possible itinerary. If the calculation failed, skip this trip check.
            if (!calculateNextItinerary()) return true;

            // check if the matching itinerary has already ended
            if (matchingItinerary.endTime.before(DateTimeUtils.nowAsDate())) {
                // itinerary is done today, advance to the next day and recheck
                targetZonedDateTime = targetZonedDateTime.plusDays(1);
                // calculate the next possible itinerary. If the calculation failed, skip this trip check.
                if (!calculateNextItinerary()) return true;
            }
            LOG.info("Next itinerary happening on {}.", targetDate);
            // save journey state with updated matching itinerary and target date
            journeyState.update(this);
        } else {
            matchingItinerary = journeyState.matchingItinerary;
            targetDate = journeyState.targetDate;
        }
        Instant tripStartInstant = Instant.ofEpochMilli(matchingItinerary.startTime.getTime());

        // Get current time and trip time (with the time offset to today) for comparison.
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime(targetZoneId);
        // TODO: Change log level.
        LOG.info("Trip starts at {} (now={})", tripStartInstant.toString(), now.toString());

        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long millisSinceLastCheck = DateTimeUtils.currentTimeMillis() - journeyState.lastCheckedMillis;
        long minutesSinceLastCheck = TimeUnit.MILLISECONDS.toMinutes(millisSinceLastCheck);
        LOG.info("{} minutes since last checking trip", minutesSinceLastCheck);
        long minutesUntilTrip = (tripStartInstant.getEpochSecond() - now.toEpochSecond()) / 60;
        LOG.info("Trip starts in {} minutes", minutesUntilTrip);
        // skip check if the time until the next trip starts is longer than the requested lead time
        if (minutesUntilTrip > trip.leadTimeInMinutes) {
            LOG.info(
                "Next trip start time is greater than lead time of {} minute(s). Skipping trip.",
                trip.leadTimeInMinutes
            );
            return true;
        }
        // If time until trip is greater than 60 minutes, we only need to check once every hour.
        if (minutesUntilTrip > 60) {
            // It's been about an hour since the last check. Do not skip.
            if (minutesSinceLastCheck >= 60) {
                // TODO: Change log level.
                LOG.info("Trip not checked in at least an hour. Checking.");
                return false;
            }
        } else {
            // It's less than an hour until the trip time, start more frequent trip checks (about every 15 minutes).
            if (minutesSinceLastCheck >= 15) {
                // Last check was more than 15 minutes ago. Check. (approx. 4 checks per hour).
                // TODO: Change log level.
                LOG.info("Trip happening soon. Checking.");
                return false;
            }
            // If the trip starts within 30 minutes, check the trip every minute (assuming the loop runs every minute).
            if (minutesUntilTrip <= 30) {
                // TODO: Change log level.
                LOG.info("Trip happening within 30 minutes. Checking every minute.");
                return false;
            }
        }
        // TODO: Check that journey state is not flagged
        // TODO: Check last notification time.
        // Default to skipping check.
        // TODO: Change log level.
        LOG.info("Trip criteria not met to check. Skipping.");
        return true;
    }

    /**
     * Calculate the next possible time that the itinerary is possible, advancing the targetZonedDateTime and setting
     * the targetZonedDateTime, targetDate, otpResponse and matchingItinerary. Returns false if an error occurs or a
     * matching itinerary isn't found on the next possible date the trip should be monitored on.
     */
    private boolean calculateNextItinerary() {
        // if the trip is not active on the current zoned date time, advance until a day is found when the trip is
        // active. It is guaranteed that the trip will be active on a certain date because a call to
        // {@link MonitoredTrip#isInactive} should already have been made in the shouldSkipMonitoredTripCheck method.
        while (!trip.isActiveOnDate(targetZonedDateTime)) {
            targetZonedDateTime = targetZonedDateTime.plusDays(1);
        }
        targetDate = targetZonedDateTime.format(DATE_FORMATTER);

        // execute trip plan request for the target time
        if (!calculateOtpResponse()) {
            // failed to calculate, return false
            return false;
        }

        // check if the resulting itinerary has a matching itinerary
        if (!findMatchingItinerary()) {
            // TODO: figure out how to notify the user that the itinerary is not possible anymore
            // for now, just say the next itinerary couldn't be found
            return false;
        }
        return true;
    }

    private boolean calculateOtpResponse() {
        OtpDispatcherResponse otpDispatcherResponse;
        try {
            otpDispatcherResponse = OtpDispatcher.sendOtpPlanRequest(generateTripPlanQueryParams());
        } catch (Exception e) {
            // TODO: report bugsnag
            LOG.error("Encountered an error while making a request ot the OTP server. error={}", e);
            return false;
        }

        if (otpDispatcherResponse == null) return false;

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
        newParams.add(new BasicNameValuePair("date", targetDate));
        for (NameValuePair param : params) {
            if (!param.getName().equals("date")) {
                newParams.add(param);
            }
        }

        // convert object to string
        return URLEncodedUtils.format(newParams, UTF_8);
    }
}
