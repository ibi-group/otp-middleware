package org.opentripplanner.middleware.tripMonitor.jobs;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.models.TripStatus;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(
        DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN
    );

    private final MonitoredTrip trip;
    /**
     * Used to track the various check trip notifications and construct email/SMS messages.
     */
    public final Set<TripMonitorNotification> notifications = new HashSet<>();
    /**
     * The index of the matching {@link Itinerary} as found in the OTP {@link OtpResponse} planned as part of this check.
     */
    public Itinerary matchingItinerary;
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

    /**
     * For testing purposes only!
     */
    public void setJourneyState(JourneyState journeyState) {
        this.journeyState = journeyState;
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
        // Update journey state.
        journeyState.update(this);
    }

    private void runCheckLogic() {
        // Make a request to OTP and find the matching itinerary. If there was an error or the matching itinerary was
        // not found, don't run the other checks.
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
    private boolean makeOTPRequestAndUpdateMatchingItinerary() {
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
                // Set the matching itinerary.
                matchingItinerary = candidateItinerary;

                // set the status according to whether the current itinerary occurs in the past, present or future
                updateTripStatus();

                // update the trip's itinerary existence data so that any invalid dates are cleared (thus resulting in
                // that day of week saying that it is a valid day of the week).
                trip.itineraryExistence.getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek()).invalidDates =
                    new ArrayList<>();
                Persistence.monitoredTrips.replace(trip.id, trip);
                return true;
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

        // update trip itinerary existence to reflect that trip was not possible on this day of the week
        trip.itineraryExistence
            .getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek())
            .handleInvalidDate(targetZonedDateTime);
        Persistence.monitoredTrips.replace(trip.id, trip);

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
        // recent the trip check was run). If no check has yet been run, this will be null.
        Itinerary latestItinerary = trip.latestItinerary();
        Set<LocalizedAlert> previousAlerts = latestItinerary == null
            ? Collections.emptySet()
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

    /**
     * Checks whether the trip is beginning or ending at a time greater than the allowable variance. This will check
     * whether the departure or arrival time of the whole journey has deviated to the point where the absolute value of
     * the variance has changed more than the trip's variance. If it has, a notification is generated.
     */
    public TripMonitorNotification checkTripForDelay(NotificationType delayType) {
        // the target time for trip depending on notification type
        Date matchingItineraryTargetTime;

        // the current baseline epoch millis to check if a new threshold has been met
        long baselineItineraryTargetEpochMillis;

        // the original target epoch millis that the trip would've started or ended at
        long originalTargetTimeEpochMillis;

        if (delayType == NotificationType.DEPARTURE_DELAY) {
            matchingItineraryTargetTime = matchingItinerary.startTime;
            baselineItineraryTargetEpochMillis = journeyState.baselineDepartureTimeEpochMillis;
            originalTargetTimeEpochMillis = journeyState.originalDepartureTimeEpochMillis;
        } else {
            matchingItineraryTargetTime = matchingItinerary.endTime;
            baselineItineraryTargetEpochMillis = journeyState.baselineArrivalTimeEpochMillis;
            originalTargetTimeEpochMillis = journeyState.originalArrivalTimeEpochMillis;
        }

        // calculate deviation from threshold in minutes
        long deviationInMinutes = Math.abs(
            TimeUnit.MINUTES.convert(
                baselineItineraryTargetEpochMillis - matchingItineraryTargetTime.getTime(),
                TimeUnit.MILLISECONDS
            )
        );

        // check if threshold met
        if (deviationInMinutes >= trip.departureVarianceMinutesThreshold) {
            // threshold met, set new baseline time
            if (delayType == NotificationType.DEPARTURE_DELAY) {
                journeyState.baselineDepartureTimeEpochMillis = matchingItineraryTargetTime.getTime();
            } else {
                journeyState.baselineArrivalTimeEpochMillis = matchingItineraryTargetTime.getTime();
            }

            // create and return notification
            return TripMonitorNotification.createDelayNotification(
                TimeUnit.MINUTES.convert(
                    matchingItineraryTargetTime.getTime() - originalTargetTimeEpochMillis,
                    TimeUnit.MILLISECONDS
                ),
                trip.departureVarianceMinutesThreshold,
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
        if (trip.isInactive()) return true;

        // get the configured timezone that OTP is using to parse dates and times
        ZoneId targetZoneId = DateTimeUtils.getOtpZoneId();

        // Get the most recent journey state and itinerary to see when the next monitored trip is supposed to occur
        journeyState = trip.retrieveJourneyState();

        // Make sure journey state indicates that the trip should still possible on at least one day of the week as of
        // the last time a matching itinerary was attempted to be found. This field in the journey state is used instead
        // of the trip's itinerary existence data in order to not skip trips that are monitored only one day of the week
        // and may have not had a matching itinerary on that single monitored day even though it could be possible the
        // next week.
        if (journeyState.noLongerPossible) return true;

        if (
            journeyState.matchingItinerary == null ||
                journeyState.matchingItinerary.endTime.before(new Date(journeyState.lastCheckedEpochMillis))
        ) {
            // Either the monitored trip hasn't ever checked on the next itinerary, or the most recent itinerary has
            // completed and the next possible one needs to be fetched in order to determine the scheduled start time of
            // the itinerary on the next possible day the monitored trip happens

            LOG.info("Calculating next itinerary for trip");

            // initialize the trip's matching itinerary to the latest journeyState's matching itinerary, or use the
            // itinerary that the trip was saved with
            matchingItinerary = journeyState.matchingItinerary == null
                // clone the trip's itinerary just in case the code attempts to save the trip (and thus the itinerary)
                ? trip.itinerary.clone()
                : journeyState.matchingItinerary;

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
            if (journeyState.targetDate != null) {
                LocalDate lastDate = DateTimeUtils.getDateFromString(
                    journeyState.targetDate,
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
            // itinerary has already ended for the day. In that case, advance until the next day.
            if (journeyState.matchingItinerary == null && trip.itinerary.endTime.after(DateTimeUtils.nowAsDate())) {
                targetZonedDateTime = targetZonedDateTime.plusDays(1);
            }

            // advance the trip to the next active date
            advanceToNextActiveTripDate();
        } else {
            matchingItinerary = journeyState.matchingItinerary;
            targetDate = journeyState.targetDate;

            // skip checking the trip the rest of the time that it is active if it had its trip status marked as the
            // next trip not being possible
            if (journeyState.tripStatus == TripStatus.NEXT_TRIP_NOT_POSSIBLE) return true;
        }
        Instant tripStartInstant = matchingItinerary.startTime.toInstant();

        // Get current time and trip time (with the time offset to today) for comparison.
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime(targetZoneId);

        // TODO: Change log level.
        LOG.info("Trip starts at {} (now={})", tripStartInstant.toString(), now.toString());

        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long millisSinceLastCheck = DateTimeUtils.currentTimeMillis() - journeyState.lastCheckedEpochMillis;
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

    private void advanceToNextActiveTripDate() throws URISyntaxException {
        // Advance the target date/time until a day is found when the trip is active.
        while (!trip.isActiveOnDate(targetZonedDateTime)) {
            targetZonedDateTime = targetZonedDateTime.plusDays(1);
        }
        targetDate = targetZonedDateTime.format(DATE_FORMATTER);

        LOG.info("Next itinerary happening on {}.", targetDate);

        // Update the matching itinerary with the expected scheduled times for when the next trip is
        // expected to happen. This is done by incrementing the matching itinerary's start or end time such that it
        // would be the either the first possible itinerary happening after the target time for depart at trips or
        // the latest possible itinerary that ends before the target time for arrive by trips. Doing so this way
        // avoids making calls to OTP and sets up an expected itinerary to check for even if OTP is unable to return
        // the itinerary due to it being suboptimal.
        long offsetMillis;
        if (trip.isArriveBy()) {
            // find the closest itinerary end time that does not exceed the target zoned date time
            Instant newEndTime = matchingItinerary.endTime.toInstant();
            do {
                newEndTime = newEndTime.plus(1, ChronoUnit.DAYS);
            } while (newEndTime.plus(1, ChronoUnit.DAYS).isBefore(targetZonedDateTime.toInstant()));
            offsetMillis = newEndTime.toEpochMilli() - matchingItinerary.endTime.getTime();
            matchingItinerary.startTime = new Date(matchingItinerary.startTime.getTime() + offsetMillis);
        } else {
            // find the first itinerary start time that does exceeds the target zoned date time
            Instant newStartTime = matchingItinerary.startTime.toInstant();
            do {
                newStartTime = newStartTime.plus(1, ChronoUnit.DAYS);
            } while (newStartTime.isBefore(targetZonedDateTime.toInstant()));
            offsetMillis = newStartTime.toEpochMilli() - matchingItinerary.startTime.getTime();
            matchingItinerary.endTime = new Date(matchingItinerary.endTime.getTime() + offsetMillis);
        }
        for (Leg leg : matchingItinerary.legs) {
            leg.startTime = new Date(leg.startTime.getTime() + offsetMillis);
            leg.endTime = new Date(leg.endTime.getTime() + offsetMillis);
        }

        // update journey state with baseline departure and arrival times which are the last known departure/arrival
        journeyState.baselineDepartureTimeEpochMillis = matchingItinerary.startTime.getTime();
        journeyState.baselineArrivalTimeEpochMillis = matchingItinerary.endTime.getTime();

        // update journey state with the original (scheduled departure and arrival times). Calculate these by
        // finding the first/last transit legs and subtracting any delay.
        journeyState.originalDepartureTimeEpochMillis = matchingItinerary.startTime.getTime();
        for (Leg leg : matchingItinerary.legs) {
            if (leg.transitLeg) {
                journeyState.originalDepartureTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.departureDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }
        journeyState.originalArrivalTimeEpochMillis = matchingItinerary.endTime.getTime();
        for (int i = matchingItinerary.legs.size() - 1; i >= 0; i--) {
            Leg leg = matchingItinerary.legs.get(i);
            if (leg.transitLeg) {
                journeyState.originalArrivalTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.arrivalDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }

        updateTripStatus();

        // save journey state with updated matching itinerary and target date
        journeyState.update(this);
    }
}
