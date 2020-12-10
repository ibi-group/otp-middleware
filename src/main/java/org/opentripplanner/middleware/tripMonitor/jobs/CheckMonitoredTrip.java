package org.opentripplanner.middleware.tripMonitor.jobs;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.tripMonitor.TripStatus;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripMonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This job handles the primary functions for checking a {@link MonitoredTrip}, including:
 * - determining if a check should be run (based on mostly date/time),
 * - making requests to OTP and comparing the stored itinerary against these new responses from OTP, and
 * - determining if notifications should be sent to the user monitoring the trip based on their saved criteria.
 */
public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);

    public final MonitoredTrip trip;

    /**
     * Used to track the various check trip notifications and construct email/SMS messages.
     */
    public final Set<TripMonitorNotification> notifications = new HashSet<>();

    /**
     * The matching itinerary from the previous run of this job.
     */
    public Itinerary previousMatchingItinerary;

    /**
     * The matching {@link Itinerary} that is used during this check. It is calculated in the following ways:
     * - the trip's Itinerary if this check is being ran for the first time.
     * - the trip's JourneyState if the check has already been performed
     * - an OTP response if the trip is currently active
     */
    public Itinerary matchingItinerary;

    /**
     * Tracks the time the notification was sent to the user.
     */
    public long notificationTimestampMillis = -1;

    /**
     * The journey state that was calculated from the prevous run of this job.
     */
    public JourneyState previousJourneyState;

    /**
     * An updated journey state that will be saved to the MonitoredTrip record after this check.
     */
    public JourneyState journeyState;

    public String targetDate;

    protected ZonedDateTime targetZonedDateTime;

    public CheckMonitoredTrip(MonitoredTrip trip) throws CloneNotSupportedException {
        this.trip = trip;
        previousJourneyState = trip.journeyState;
        journeyState = previousJourneyState.clone();
        previousMatchingItinerary = trip.journeyState.matchingItinerary;
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

        // Check monitored trip.
        runCheckLogic();
        // Send notifications to user. This should happen before updating the journey state so that we can check the
        // last notification sent.
        sendNotifications();
        // Update trip and journey state.
        updateMonitoredTrip();
    }

    private void runCheckLogic() {
        // Make a request to OTP and find the matching itinerary. If there was an error or the matching itinerary was
        // not found or the trip is no longer active, don't run the other checks.
        if (!makeOTPRequestAndUpdateMatchingItinerary()) {
            return;
        }
        // Matching itinerary found in OTP response. Run real-time checks.
        enqueueNotification(
            // Check for notifications related to service alerts.
            checkTripForNewAlerts(),
            // Check for notifications related to delays.
            checkTripForDelay(NotificationType.DEPARTURE_DELAY),
            checkTripForDelay(NotificationType.ARRIVAL_DELAY)
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
    public boolean makeOTPRequestAndUpdateMatchingItinerary() {
        OtpDispatcherResponse otpDispatcherResponse;
        try {
            // Generate the appropriate OTP query params for the trip for the current check by replacing the date query
            // parameter with the appropriate date.
            Map<String, String> params = trip.parseQueryParams();

            // building a new list by copying all values, except for the date which is set to the target date
            List<NameValuePair> newParams = new ArrayList<>();
            newParams.add(new BasicNameValuePair("date", targetDate));
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (!param.getKey().equals("date")) {
                    newParams.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                }
            }
            otpDispatcherResponse = OtpDispatcher.sendOtpPlanRequest(URLEncodedUtils.format(newParams, UTF_8));
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
        OtpResponse otpResponse = otpDispatcherResponse.getResponse();
        for (int i = 0; i < otpResponse.plan.itineraries.size(); i++) {
            Itinerary candidateItinerary = otpResponse.plan.itineraries.get(i);
            if (ItineraryUtils.itinerariesMatch(trip.itinerary, candidateItinerary)) {
                // matching itinerary found!
                LOG.info("Found matching itinerary!");

                // Set the matching itinerary.
                matchingItinerary = candidateItinerary;

                // update the journey state with whether the matching itinerary has realtime data
                journeyState.hasRealtimeData = false;
                for (Leg leg : matchingItinerary.legs) {
                    if (leg.realTime) {
                        journeyState.hasRealtimeData = true;
                        break;
                    }
                }

                // set the status according to whether the current itinerary occurs in the past, present or future
                updateTripStatus();

                // update the trip's itinerary existence data so that any invalid dates are cleared (thus resulting in
                // that day of week saying that it is a valid day of the week).
                trip.itineraryExistence.getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek()).invalidDates =
                    new ArrayList<>();

                // If the updated trip status is upcoming and the end time of the current matching itinerary is in the
                // past, this means the trip has completed and the next possible time the trip occurs should be
                // calculated
                if (
                    journeyState.tripStatus == TripStatus.TRIP_UPCOMING &&
                        matchingItinerary.endTime.before(DateTimeUtils.nowAsDate())
                ) {
                    LOG.info("Matching Itinerary has concluded, advancing to next possible trip date.");
                    targetZonedDateTime = targetZonedDateTime.plusDays(1);
                    advanceToNextActiveTripDate();
                    updateMonitoredTrip();

                    // return false to indicate that no further checks for delays/alerts/etc should occur
                    return false;
                }

                LOG.info("Trip status set to {}", journeyState.tripStatus);
                return updateMonitoredTrip();
            }
        }

        // If this point is reached, a matching itinerary was not found
        LOG.warn("No comparison itinerary found in otp response for trip");

        // Check whether this trip should no longer ever be checked due to not having matching itineraries on any
        // monitored day of the week. For trips that are only monitored on one day of the week, they could have been not
        // possible for just that day, but could again be possible the next week. Therefore, this checks if the trip
        // was not possible on all monitored days of the previous week and if so, it updates the journeyState to say
        // that the trip is no longer possible.
        boolean noMatchingItineraryFoundOnPreviousChecks = !trip.itineraryExistence.
            isStillPossibleOnAtLeastOneMonitoredDayOfTheWeek(
                trip
            );
        journeyState.tripStatus = noMatchingItineraryFoundOnPreviousChecks
            ? TripStatus.NO_LONGER_POSSIBLE
            : TripStatus.NEXT_TRIP_NOT_POSSIBLE;
        journeyState.noLongerPossible = noMatchingItineraryFoundOnPreviousChecks;

        LOG.info(
            noMatchingItineraryFoundOnPreviousChecks
                ? "Trip checking has no more possible days to check, TRIP NO LONGER POSSIBLE!"
                : "Trip is not possible today, will check again next week."
        );

        // update trip itinerary existence to reflect that trip was not possible on this day of the week
        trip.itineraryExistence
            .getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek())
            .handleInvalidDate(targetZonedDateTime);
        updateMonitoredTrip();

        // send an appropriate notification if the trip is still possible on another day of the week, or if it is now
        // not possible on any day of the week that the trip should be monitored
        enqueueNotification(
            TripMonitorNotification.createItineraryNotFoundNotification(noMatchingItineraryFoundOnPreviousChecks)
        );
        return false;
    }

    /**
     * Updates the journey state's trip status according to whether the matching itinerary occurs in the past, present
     * or future
     */
    private void updateTripStatus() {
        Instant now = DateTimeUtils.nowAsDate().toInstant();
        boolean tripIsActive = matchingItinerary.startTime.toInstant().isBefore(now) &&
            matchingItinerary.endTime.toInstant().isAfter(now);
        journeyState.tripStatus = tripIsActive ? TripStatus.TRIP_ACTIVE : TripStatus.TRIP_UPCOMING;
    }

    public TripMonitorNotification checkTripForNewAlerts() {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip. Skipping check.");
            return null;
        }
        // Get the previously checked itinerary/alerts from the journey state (i.e., the response from OTP the most
        // recent the trip check was run). If no check has yet been run, this will be null.=
        Set<LocalizedAlert> previousAlerts = previousMatchingItinerary == null
            ? Collections.emptySet()
            : new HashSet<>(previousMatchingItinerary.getAlerts());
        // Construct set from new alerts.
        Set<LocalizedAlert> newAlerts = new HashSet<>(matchingItinerary.getAlerts());
        TripMonitorNotification notification = TripMonitorNotification.createAlertNotification(previousAlerts, newAlerts);
        if (notification == null) {
            // TODO: Change log level
            LOG.info("No unseen/resolved alerts found for trip.");
        }
        return notification;
    }

    /**
     * Checks whether the trip is beginning or ending at a time greater than the allowable variance relative to the
     * baseline itinerary arrival or departure time. See docs about baseline and scheduled times here:
     * {@link JourneyState#baselineArrivalTimeEpochMillis}. This will check whether the departure or arrival time of the
     * whole journey has deviated to the point where the absolute value of the variance has changed more than the
     * variance threshold set for generating a notification about the trip's delays. If it has, a notification is
     * generated.
     *
     * Example (departure):
     * - Deviation threshold: 10 minutes
     * - Scheduled departure time: 5:00pm
     * - Baseline departure time: 5:00pm
     * - Current realtime departure time: 5:08pm
     * - Result: The threshold is not met, so no notification is sent.
     *
     * Example (arrival):
     * - Deviation threshold: 10 minutes
     * - Scheduled arrival time: 6:00pm
     * - Baseline arrival time: 6:11pm (a previous check sent out a notification once the trip become more than 10
     *     minutes late. Following that, the baseline arrival time was updated accordingly)
     * - Current realtime departure time: 5:58pm
     * - Result: The threshold is met, so a notification is sent.
     */
    public TripMonitorNotification checkTripForDelay(NotificationType delayType) {
        // the target time for trip depending on notification type (the matching itinerary's start time if checking for
        // departure delay, or the matching itinerary's end time if checking for arrival delay)
        Date matchingItineraryTargetTime;

        // the current baseline epoch millis to check if a new threshold has been met (the baseline departure time if
        // checking for departure delay, or the baseline arrival time if checking for arrival delay)
        long baselineItineraryTargetEpochMillis;

        // the scheduled target epoch millis that the trip would've started or ended at (the scheduled departure time if
        // checking for departure delay, or the scheduled arrival time if checking for arrival delay)
        long scheduledTargetTimeEpochMillis;

        // the threshold of deviation to check (this can be different for arrival or departure thresholds).
        int deviationThreshold;

        if (delayType == NotificationType.DEPARTURE_DELAY) {
            matchingItineraryTargetTime = matchingItinerary.startTime;
            baselineItineraryTargetEpochMillis = journeyState.baselineDepartureTimeEpochMillis;
            scheduledTargetTimeEpochMillis = journeyState.scheduledDepartureTimeEpochMillis;
            deviationThreshold = trip.departureVarianceMinutesThreshold;
        } else {
            matchingItineraryTargetTime = matchingItinerary.endTime;
            baselineItineraryTargetEpochMillis = journeyState.baselineArrivalTimeEpochMillis;
            scheduledTargetTimeEpochMillis = journeyState.scheduledArrivalTimeEpochMillis;
            deviationThreshold = trip.arrivalVarianceMinutesThreshold;
        }

        // calculate absolute deviation of current itinerary target time from the baseline target time in minutes
        long deviationAbsoluteMinutes = Math.abs(
            TimeUnit.MINUTES.convert(
                baselineItineraryTargetEpochMillis - matchingItineraryTargetTime.getTime(),
                TimeUnit.MILLISECONDS
            )
        );

        // check if threshold met
        if (deviationAbsoluteMinutes >= deviationThreshold) {
            // threshold met, set new baseline time
            if (delayType == NotificationType.DEPARTURE_DELAY) {
                journeyState.baselineDepartureTimeEpochMillis = matchingItineraryTargetTime.getTime();
            } else {
                journeyState.baselineArrivalTimeEpochMillis = matchingItineraryTargetTime.getTime();
            }

            // create and return notification
            long delayMinutes = TimeUnit.MINUTES.convert(
                matchingItineraryTargetTime.getTime() - scheduledTargetTimeEpochMillis,
                TimeUnit.MILLISECONDS
            );
            return TripMonitorNotification.createDelayNotification(
                delayMinutes,
                matchingItineraryTargetTime,
                delayType
            );
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
        if (previousJourneyState.lastNotifications.containsAll(notifications)) {
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
     * - the current time is before the lead time before the next itinerary starts
     * - the current time is after the lead time before the next itinerary starts, but is over an hour until the
     *     itinerary start time and the trip has already been checked within the last 60 minutes
     * - the current time is after the lead time before the next itinerary starts and between 60-30 minutes prior to the
     *     itinerary start time, but a check has occurred within the last 15 minutes
     *
     * These checks are done based off of the information in the trip's journey state's latest itinerary. If no such
     * itinerary exists or a previous monitored trip's itinerary has completed, then the next possible itinerary will be
     * calculated and updated in the monitored trip's journey state.
     */
    public boolean shouldSkipMonitoredTripCheck() throws Exception {
        // before anything else, return true if the trip is inactive
        if (trip.isInactive()) {
            LOG.info("Skipping: Trip is inactive.");
            return true;
        }

        // get the configured timezone that OTP is using to parse dates and times
        ZoneId targetZoneId = DateTimeUtils.getOtpZoneId();

        // Make sure journey state indicates that the trip should still possible on at least one day of the week as of
        // the last time a matching itinerary was attempted to be found. This field in the journey state is used instead
        // of the trip's itinerary existence data in order to not skip trips that are monitored only one day of the week
        // and may have not had a matching itinerary on that single monitored day even though it could be possible the
        // next week.
        if (previousJourneyState.noLongerPossible) {
            LOG.info("Skipping: Trip is no longer possible.");
            return true;
        }

        // Check if the previous matching itinerary was null or if it has already concluded
        boolean matchingItineraryActiveOrUpcoming = previousMatchingItinerary != null &&
            previousMatchingItinerary.endTime.after(new Date(previousJourneyState.lastCheckedEpochMillis));
        if (matchingItineraryActiveOrUpcoming) {
            // skip checking the trip the rest of the time that it is active if it had its trip status marked as the
            // next trip not being possible
            if (previousJourneyState.tripStatus == TripStatus.NEXT_TRIP_NOT_POSSIBLE) {
                LOG.info("Skipping: Next trip is not possible.");
                return true;
            }

            // skip checking the trip if it has been snoozed
            if (trip.snoozed) {
                LOG.info("Skipping: Trip is snoozed.");
                return true;
            }

            matchingItinerary = previousMatchingItinerary;
            targetDate = previousJourneyState.targetDate;
            targetZonedDateTime = DateTimeUtils.makeOtpZonedDateTime(targetDate, trip.tripTime);
        } else {
            // Either the monitored trip hasn't ever checked on the next itinerary, or the most recent itinerary has
            // completed and the next possible one needs to be fetched in order to determine the scheduled start time of
            // the itinerary on the next possible day the monitored trip happens

            LOG.info("Calculating next itinerary for trip");

            // initialize the trip's journey state and matching itinerary to the latest journeyState's matching
            // itinerary, or use the itinerary that the trip was saved with
            if (previousMatchingItinerary == null) {
                // clone the trip's itinerary just in case the code attempts to save the trip (and thus the itinerary)
                matchingItinerary = trip.itinerary.clone();
            } else {
                matchingItinerary = previousMatchingItinerary.clone();
            }

            // calculate target time for the next trip plan request
            // find the next possible day the trip is active by initializing the the appropriate target time. Start by
            // checking today's date at the earliest in case the user has paused trip monitoring for a while
            targetZonedDateTime = DateTimeUtils.nowAsZonedDateTime(targetZoneId)
                .withHour(trip.tripTimeHour())
                .withMinute(trip.tripTimeMinute())
                .withSecond(0);

            // Check if the journeyState indicates that an itinerary has already been calculated in a previous run of
            // this CheckMonitoredTrip. If the targetDate is null, then the current date has not yet been checked. If
            // the journeyState's targetDate is not null, that indicates that today has already been checked. Therefore,
            // advance targetDate by another day before calculating when the next itinerary occurs.
            if (previousJourneyState.targetDate != null) {
                LocalDate lastDate = DateTimeUtils.getDateFromString(
                    previousJourneyState.targetDate,
                    DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN
                );
                if (
                    lastDate.getYear() == targetZonedDateTime.getYear() &&
                        lastDate.getMonthValue() == targetZonedDateTime.getMonthValue() &&
                        lastDate.getDayOfMonth() == targetZonedDateTime.getDayOfMonth()
                ) {
                    targetZonedDateTime = targetZonedDateTime.plusDays(1);
                }
            }

            // Check if the CheckMonitoredTrip is being ran for the first time for this trip and if the trip's saved
            // itinerary has already ended. Additionally, make sure that the saved itinerary occurred on the same
            // service day. If both of these conditions are true, then there is no need to check for the current day and
            // the target zoned date time should be advanced to the next day.
            if (
                previousMatchingItinerary == null &&
                    trip.itinerary.endTime.before(DateTimeUtils.nowAsDate()) &&
                    ItineraryUtils.occursOnSameServiceDay(trip.itinerary, targetZonedDateTime, trip.arriveBy)
            ) {
                targetZonedDateTime = targetZonedDateTime.plusDays(1);
            }

            // advance the trip to the next active date
            advanceToNextActiveTripDate();

            // save journey state with updated matching itinerary and target date
            if (!updateMonitoredTrip()) {
                // trip no longer exists, skip check
                LOG.info("Skipping: Trip no longer exists.");
                return true;
            }
        }

        Instant tripStartInstant = matchingItinerary.startTime.toInstant();

        // Get current time and trip time (with the time offset to today) for comparison.
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime(targetZoneId);

        // TODO: Change log level.
        LOG.info("Trip starts at {} (now={})", tripStartInstant.toString(), now.toString());

        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long millisSinceLastCheck = DateTimeUtils.currentTimeMillis() - previousJourneyState.lastCheckedEpochMillis;
        long minutesSinceLastCheck = TimeUnit.MILLISECONDS.toMinutes(millisSinceLastCheck);
        LOG.info("{} minutes since last checking trip", minutesSinceLastCheck);
        long minutesUntilTrip = (tripStartInstant.getEpochSecond() - now.toEpochSecond()) / 60;
        LOG.info("Trip starts in {} minutes", minutesUntilTrip);
        // skip check if the time until the next trip starts is longer than the requested lead time
        if (minutesUntilTrip > trip.leadTimeInMinutes) {
            LOG.info(
                "The time until this trip begins again is more than the {}-minute lead time. Skipping trip.",
                trip.leadTimeInMinutes
            );
            return true;
        }
        // If time until trip is greater than 60 minutes, we only need to check once every hour.
        if (minutesUntilTrip > 60) {
            // It's been about an hour since the last check. Do not skip.
            int overHourCheckThresholdMinutes = 60;
            if (minutesSinceLastCheck >= overHourCheckThresholdMinutes) {
                // TODO: Change log level.
                LOG.info("Trip not checked in at least an {} minutes. Checking.", overHourCheckThresholdMinutes);
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
            int checkEveryMinuteThresholdMinutes = 30;
            if (minutesUntilTrip <= checkEveryMinuteThresholdMinutes) {
                // TODO: Change log level.
                LOG.info("Trip happening within {} minutes. Checking every minute.", checkEveryMinuteThresholdMinutes);
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

    private void advanceToNextActiveTripDate() {
        // Advance the target date/time until a day is found when the trip is active.
        while (!trip.isActiveOnDate(targetZonedDateTime)) {
            targetZonedDateTime = targetZonedDateTime.plusDays(1);
        }
        targetDate = targetZonedDateTime.format(DateTimeUtils.DEFAULT_DATE_FORMATTER);

        LOG.info("Next itinerary happening on {}.", targetDate);

        // Update the matching itinerary with the expected scheduled times for when the next trip is
        // expected to happen. This is done by incrementing the matching itinerary's start or end time such that it
        // would be the either the first possible itinerary happening after the target time for depart at trips or
        // the latest possible itinerary that ends before the target time for arrive by trips. Doing so this way
        // avoids making calls to OTP and sets up an expected itinerary to check for even if OTP is unable to return
        // the itinerary due to it being suboptimal.
        long offsetMillis;
        if (trip.arriveBy) {
            // find the closest itinerary end time that does not exceed the target zoned date time
            ZonedDateTime newEndTime = DateTimeUtils.makeOtpZonedDateTime(matchingItinerary.endTime);
            do {
                newEndTime = newEndTime.plusDays(1);
            } while (newEndTime.plusDays(1).isBefore(targetZonedDateTime));
            offsetMillis = newEndTime.toInstant().toEpochMilli() - matchingItinerary.endTime.getTime();
        } else {
            // find the first itinerary start time that does exceeds the target zoned date time
            ZonedDateTime newStartTime = DateTimeUtils.makeOtpZonedDateTime(matchingItinerary.startTime);
            while (newStartTime.isBefore(targetZonedDateTime)) {
                newStartTime = newStartTime.plusDays(1);
            }
            offsetMillis = newStartTime.toInstant().toEpochMilli() - matchingItinerary.startTime.getTime();
        }

        // update overall itinerary and leg start/end times by adding offset
        matchingItinerary.startTime = new Date(matchingItinerary.startTime.getTime() + offsetMillis);
        matchingItinerary.endTime = new Date(matchingItinerary.endTime.getTime() + offsetMillis);
        for (Leg leg : matchingItinerary.legs) {
            leg.startTime = new Date(leg.startTime.getTime() + offsetMillis);
            leg.endTime = new Date(leg.endTime.getTime() + offsetMillis);
        }

        LOG.info("Next matching itinerary starts at {}", matchingItinerary.startTime);

        // update journey state with baseline departure and arrival times which are the last known departure/arrival
        journeyState.baselineDepartureTimeEpochMillis = matchingItinerary.startTime.getTime();
        journeyState.baselineArrivalTimeEpochMillis = matchingItinerary.endTime.getTime();

        // update journey state with the original (scheduled departure and arrival times). Calculate these by
        // finding the first/last transit legs and subtracting any delay.
        journeyState.scheduledDepartureTimeEpochMillis = matchingItinerary.startTime.getTime();
        for (Leg leg : matchingItinerary.legs) {
            if (leg.transitLeg) {
                journeyState.scheduledDepartureTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.departureDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }
        journeyState.scheduledArrivalTimeEpochMillis = matchingItinerary.endTime.getTime();
        for (int i = matchingItinerary.legs.size() - 1; i >= 0; i--) {
            Leg leg = matchingItinerary.legs.get(i);
            if (leg.transitLeg) {
                journeyState.scheduledArrivalTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.arrivalDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }

        journeyState.hasRealtimeData = false;

        // reset the snoozed parameter to false
        trip.snoozed = false;
        updateTripStatus();
    }

    /**
     * Update the monitored trip with the updated journey state with updated matching itinerary and target date.
     */
    private boolean updateMonitoredTrip() {
        // make sure the trip still exists before saving it. It is possible that the user deleted the trip after this
        // job started but before this database update.
        if (Persistence.monitoredTrips.getById(trip.id) == null) {
            // trip has been deleted!
            return false;
        }
        journeyState.matchingItinerary = matchingItinerary;
        journeyState.targetDate = targetDate;
        journeyState.lastCheckedEpochMillis = DateTimeUtils.currentTimeMillis();
        // Update notification time if notification successfully sent.
        if (notificationTimestampMillis != -1) {
            journeyState.lastNotificationTimeMillis = notificationTimestampMillis;
        }
        trip.journeyState = journeyState;
        Persistence.monitoredTrips.replace(trip.id, trip);
        return true;
    }
}
