package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.LegTransitionNotification;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.models.TripMonitorAlertNotification;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.tripmonitor.TripStatus;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripTrackingData;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.I18nUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.opentripplanner.middleware.models.LegTransitionNotification.getLegTransitionNotifyUsers;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMATTER;
import static org.opentripplanner.middleware.utils.DateTimeUtils.diffInMinutes;
import static org.opentripplanner.middleware.utils.DateTimeUtils.makeOtpZonedDateTime;

/**
 * This job handles the primary functions for checking a {@link MonitoredTrip}, including:
 * - determining if a check should be run (based on mostly date/time),
 * - making requests to OTP and comparing the stored itinerary against these new responses from OTP, and
 * - determining if notifications should be sent to the user monitoring the trip based on their saved criteria.
 */
public class CheckMonitoredTrip implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTrip.class);
    private static final Logger ITINERARY_NOT_FOUND_LOGGER = LoggerFactory.getLogger("itinerary-not-found-logger");

    public boolean IS_TEST = false;

    public static final int MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS =
        ConfigUtils.getConfigPropertyAsInt("MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS", 3);

    public static final String ACCOUNT_PATH = "/#/account";

    public static final String SETTINGS_PATH = ACCOUNT_PATH + "/settings";

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
     * The journey state that was calculated from the previous run of this job.
     */
    public JourneyState previousJourneyState;

    /**
     * An updated journey state that will be saved to the MonitoredTrip record after this check.
     */
    public JourneyState journeyState;

    /**
     * The target datetime for the next itinerary to be oriented around. For arrive by trips, this will be the time that
     * the trip is supposed to arrive by. For depart at trips, this will be the time that the trip should start after.
     */
    ZonedDateTime targetZonedDateTime;

    /** Caches the user associated with a trip */
    private OtpUser cachedUser;

    /** Whether an attempt has been made to retrieve the user. */
    private boolean userChecked;

    /** Contains the initial reminder notification, if any is needed for this check. */
    TripMonitorNotification initialReminderNotification;

    /** The OTP Response provider */
    private Supplier<OtpResponse> otpResponseProvider;

    private final boolean hasTolerantItineraryCheck;

    public CheckMonitoredTrip(MonitoredTrip trip) throws CloneNotSupportedException {
        this(trip, true);
    }

    public CheckMonitoredTrip(MonitoredTrip trip, boolean hasTolerantItineraryCheck) throws CloneNotSupportedException {
        this.trip = trip;
        this.hasTolerantItineraryCheck = hasTolerantItineraryCheck;
        previousJourneyState = trip.journeyState;
        journeyState = previousJourneyState.clone();
        previousMatchingItinerary = trip.journeyState.matchingItinerary;
        otpResponseProvider = this::getOtpResponse;
    }

    public CheckMonitoredTrip(MonitoredTrip trip, Supplier<OtpResponse> otpResponseProvider) throws CloneNotSupportedException {
        this(trip, false);
        this.otpResponseProvider = otpResponseProvider;
    }

    public CheckMonitoredTrip(
        MonitoredTrip trip,
        Supplier<OtpResponse> otpResponseProvider,
        boolean hasTolerantItineraryCheck
    ) throws CloneNotSupportedException {
        this(trip, hasTolerantItineraryCheck);
        this.otpResponseProvider = otpResponseProvider;
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
            if (shouldSkipMonitoredTripCheck() && (
                !trip.isActive ||
                trip.snoozed || (
                    // Perform the check if the journey state or target date is not consistent with the matching itinerary.
                    trip.tripStateIsConsistentWithMatchingItinerary() &&
                    trip.tripTargetDateIsConsistentWithMatchingItinerary()
                )
            )) {
                LOG.debug("Skipping check for trip");
                return;
            }
        } catch (Exception e) {
            // TODO: report to bugsnag
            LOG.error("Encountered an error while checking the monitored trip.", e);
            return;
        }

        // Check monitored trip.
        runCheckLogic();
        // Initial reminder notification, if needed, with text based on other notifications for this trip.
        addInitialReminderIfNeeded();
        // Send notifications to user. This should happen before updating the journey state so that we can check the
        // last notification sent.
        sendNotifications();
        // Update trip and journey state.
        updateMonitoredTrip();
    }

    /**
     * Determine whether to send an initial "reminder" notification through the user's enabled notification channels.
     * The initial reminder is sent the first time a check for a trip that is active today
     * occurs within the monitoring lead time.
     */
    private void addInitialReminderIfNeeded() {
        if (shouldSendInitialReminder()) {
            initialReminderNotification = TripMonitorNotification.createInitialReminderNotification(
                trip,
                getOtpUserLocale()
            );
        }
    }

    /**
     * Whether an initial trip reminder should be sent.
     */
    public boolean shouldSendInitialReminder() {
        TripStatus tripStatus = trip.journeyState.tripStatus;
        return trip.isActive &&
            !trip.snoozed &&
            trip.notifyAtLeadingInterval &&
            (tripStatus == TripStatus.TRIP_UPCOMING || tripStatus == TripStatus.TRIP_ACTIVE) &&
            DateTimeUtils.convertToLocalDateTime(matchingItinerary.startTime).toLocalDate().equals(DateTimeUtils.nowAsLocalDate()) &&
            isFirstTimeCheckWithinLeadMonitoringTime();
    }

    /**
     * @return true if the previous check was outside the monitoring lead time and this check is inside.
     */
    private boolean isFirstTimeCheckWithinLeadMonitoringTime() {
        long minutesSinceLastCheck = getMinutesSinceLastCheck();
        long minutesUntilTrip = getMinutesUntilTrip();
        return minutesUntilTrip <= trip.leadTimeInMinutes && minutesUntilTrip + minutesSinceLastCheck > trip.leadTimeInMinutes;
    }

    private void runCheckLogic() {
        // Make a request to OTP and find the matching itinerary. If there was an error or the matching itinerary was
        // not found or the trip is no longer active, don't run the other checks.
        if (!checkOtpAndUpdateTripStatus()) {
            updateMonitoredTrip();
            return;
        }
        // Matching itinerary found in OTP response. Run real-time checks.
        enqueueNotification(
            // Check for notifications related to service alerts.
            checkTripForNewAlerts(),
            // Check for notifications related to delays.
            checkTripForDelays()
        );
    }

    /**
     * Find and set the matching itinerary from the OTP response, and update trip status accordingly.
     * @return false to indicate that no further checks for delays/alerts/etc should occur, true otherwise.
     */
    public boolean checkOtpAndUpdateTripStatus() {
        // If matching itinerary has concluded, and live tracking is ongoing or the trip is one-time, don't check OTP.
        boolean oneTime = trip.isOneTime();
        boolean trackingOngoing = isTrackingOngoing();
        Itinerary matchingItin = trip.journeyState.matchingItinerary;
        if ((oneTime || trackingOngoing) && (matchingItin == null || matchingItin.hasEnded())) {
            if (oneTime && !trackingOngoing) {
                trip.journeyState.tripStatus = TripStatus.PAST_TRIP;
            }
            return false;
        }
        // Perform normal OTP checks otherwise.
        return makeOTPRequestAndUpdateMatchingItineraryInternal();
    }

    private boolean isTrackingOngoing() {
        return trip.journeyState.tripStatus == TripStatus.TRIP_ACTIVE && TripTrackingData.getOngoingTrackedJourney(trip.id) != null;
    }

    /**
     * Process leg transition notifications by getting all qualifying users and enqueuing relevant notifications. The
     * matching itinerary is required when updating the monitored trip. There is no requirement to match the itinerary
     * to that returned from OTP, so the existing trip itinerary is used and therefore preserved.
     */
    public void processLegTransition(NotificationType notificationType, TravelerPosition travelerPosition) throws CloneNotSupportedException {
        if (trip.journeyState.matchingItinerary != null) {
            matchingItinerary = trip.journeyState.matchingItinerary.clone();
        }
        OtpUser tripOwner = getOtpUser();
        Set<OtpUser> notifyUsers = getLegTransitionNotifyUsers(trip);
        notifyUsers.forEach(observer -> {
            if (observer != null) {
                enqueueNotification(
                    new LegTransitionNotification(
                        tripOwner.getDisplayedName(),
                        notificationType,
                        travelerPosition,
                        I18nUtils.getOtpUserLocale(observer)
                    ).tripMonitorNotification
                );
                sendNotifications(observer);
            }
        });

        updateMonitoredTrip();
    }

    /**
     * Find and set the matching itinerary from the OTP response that matches the monitored trip's stored itinerary if a
     * match exists.
     * @return false to indicate that no further checks for delays/alerts/etc should occur, true otherwise.
     *
     * FIXME: the itinerary might actually still be possible, but for some reason the OTP plan didn't find the same
     *          match. Some additional checks should be performed to make sure the itinerary really isn't possible by
     *          verifying that the same transit schedule/routes exist and that the street network is the same
     */
    private boolean makeOTPRequestAndUpdateMatchingItineraryInternal() {
        OtpResponse otpResponse = otpResponseProvider.get();
        if (otpResponse == null) {
            LOG.warn("No comparison itinerary found for trip {} - OTP response was null.", trip.id);
            return false;
        }
        for (int i = 0; i < otpResponse.plan.itineraries.size(); i++) {
            Itinerary candidateItinerary = otpResponse.plan.itineraries.get(i);
            if (ItineraryUtils.itinerariesMatch(trip.itinerary, candidateItinerary)) {
                // matching itinerary found!
                LOG.info("Found matching itinerary!");
                trip.attemptsToGetMatchingItinerary = 0;

                // Set the matching itinerary. Compute target date and set the baseline journey state.
                matchingItinerary = candidateItinerary;
                computeTargetZonedDateTime();
                resetJourneyState();

                // update the journey state with whether the matching itinerary has realtime data
                journeyState.hasRealtimeData = matchingItinerary.legs.stream().anyMatch(leg -> leg.realTime);

                // set the status according to whether the current itinerary occurs in the past, present or future
                updateTripStatus();

                if (trip.itineraryExistence != null) {
                    // update the trip's itinerary existence data so that any invalid dates are cleared (thus resulting
                    // in that day of week saying that it is a valid day of the week).
                    ItineraryExistence.ItineraryExistenceResult itinExistenceTargetDay = trip
                        .itineraryExistence
                        .getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek());
                    if (itinExistenceTargetDay != null) {
                        itinExistenceTargetDay.invalidDates = new ArrayList<>();
                    }
                }

                if (trip.isOneTime() &&
                    (journeyState.tripStatus == TripStatus.TRIP_UPCOMING || journeyState.tripStatus == TripStatus.TRIP_ACTIVE)
                ) {
                    updateMonitoredTrip();
                    return true;
                }

                // If the updated trip status is upcoming and the end time of the current matching itinerary is in the
                // past, this means the trip has completed and the next possible time the trip occurs should be
                // calculated.
                // If the matching itinerary is in the future, make sure that the target date reflects that.
                if (journeyState.tripStatus == TripStatus.TRIP_UPCOMING && (!matchingItinerary.isActive())) {
                    updateMonitoredTrip();

                    if (matchingItinerary.hasEnded()) {
                        // If today's itinerary has ended, return false to indicate that no further checks
                        // for delays/alerts/etc should occur for today.
                        return false;
                    }
                }

                LOG.info("Trip status set to {}", journeyState.tripStatus);
                return updateMonitoredTrip();
            }
        }

        // If this point is reached, a matching itinerary was not found.
        ItineraryExistence.logItineraryNotFound(
            "No comparison itinerary found",
            trip,
            otpResponse.plan,
            ITINERARY_NOT_FOUND_LOGGER
        );

        boolean setNullItinerary = !shouldPersistMatchingItinerary();
        if (hasReachedMaxItineraryChecks() || setNullItinerary) {
            // Check whether this trip should no longer ever be checked due to not having matching itineraries on any
            // monitored day of the week. For trips that are only monitored on one day of the week, they could have been not
            // possible for just that day, but could again be possible the next week. Therefore, this checks if the trip
            // was not possible on all monitored days of the previous week and if so, it updates the journeyState to say
            // that the trip is no longer possible.
            boolean noMatchingItineraryFoundOnPreviousChecks =
                !trip.itineraryExistence.isPossibleOnAtLeastOneMonitoredDayOfTheWeek(trip);

            // Record a null matching itinerary if "today" is not the target trip date or the one-time trip date.
            if (setNullItinerary) {
                matchingItinerary = null;
            }

            if (noMatchingItineraryFoundOnPreviousChecks) {
                journeyState.tripStatus = TripStatus.NO_LONGER_POSSIBLE;
                LOG.info("Trip checking has no more possible days to check, TRIP NO LONGER POSSIBLE!");

                // update trip itinerary existence to reflect that trip was not possible on this day of the week
                trip.itineraryExistence
                    .getResultForDayOfWeek(targetZonedDateTime.getDayOfWeek())
                    .handleInvalidDate(targetZonedDateTime);

            } else {
                journeyState.tripStatus = TripStatus.NEXT_TRIP_NOT_POSSIBLE;
                trip.snoozed = true;
                trip.attemptsToGetMatchingItinerary = 0;
                LOG.info("Trip for today was not found after the allowed attempts. Snoozing for today.");
                // Delete previous such notifications to ensure this one gets sent.
                previousJourneyState.lastNotifications.removeIf(n -> n.type == NotificationType.ITINERARY_NOT_FOUND);
            }

            updateMonitoredTrip();

            // send an appropriate notification if the trip is still possible on another day of the week, or if it is now
            // not possible on any day of the week that the trip should be monitored
            enqueueNotification(
                TripMonitorNotification.createItineraryNotFoundNotification(
                    !noMatchingItineraryFoundOnPreviousChecks,
                    getOtpUserLocale()
                )
            );
        } else if (matchingItinerary != null) {
            // Set/reset the trip status according to the existing matching itinerary while attempting to get a new one.
            updateTripStatus();
            updateMonitoredTrip();
        }
        return false;
    }

    /**
     * If the OTP response does not contain the expected itinerary, check the number of attempts made and decide whether
     * to try again or stop checking.
     */
    private boolean hasReachedMaxItineraryChecks() {
        if (!hasTolerantItineraryCheck) {
            LOG.info("Tolerant itinerary check disabled.");
            return true;
        }
        trip.attemptsToGetMatchingItinerary++;
        LOG.info(
            "Attempt {} of {} to get matching itinerary.",
            trip.attemptsToGetMatchingItinerary,
            MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS
        );
        return trip.attemptsToGetMatchingItinerary >= MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS;
    }

    /** Default implementation for OtpResponse provider that actually invokes the OTP server. */
    private OtpResponse getOtpResponse() {
        return OtpDispatcher.sendOtpRequestWithErrorHandling(getQueryParamsForTargetZonedDateTime());
    }

    /**
     * Generate the appropriate OTP query params for the trip for the current check by replacing the date query
     * parameter with the appropriate date.
     */
    private OtpGraphQLVariables getQueryParamsForTargetZonedDateTime() {
        OtpGraphQLVariables params = trip.otp2QueryParams.clone();
        params.date = targetZonedDateTime.format(DEFAULT_DATE_FORMATTER);
        checkForRerouting(params);
        return params;
    }

    /**
     * Get the latest tracked journey associated with this trip, if available and live. If rerouting has occurred as
     * part of that tracked journey, use the last rerouting location as the 'from place' instead of the original
     * attributed to the trip.
     */
    public void checkForRerouting(OtpGraphQLVariables params) {
        if (trip.journeyState.tripStatus == TripStatus.TRIP_ACTIVE) {
            TrackedJourney trackedJourney = TripTrackingData.getOngoingTrackedJourney(trip.id);
            if (trackedJourney != null) {
                String reroutingLocation = trackedJourney.getLastReroutingLocation();
                if (reroutingLocation != null) {
                    params.fromPlace = reroutingLocation;
                }
            }
        }
    }

    /**
     * Updates the journey state's trip status according to whether the matching itinerary occurs in the past, present
     * or future
     */
    private void updateTripStatus() {
        if (matchingItinerary.isActive()) {
            journeyState.tripStatus = TripStatus.TRIP_ACTIVE;
        } else {
            journeyState.tripStatus = DateTimeUtils.nowAsZonedDateTime().isBefore(targetZonedDateTime)
                ? TripStatus.TRIP_UPCOMING
                : TripStatus.TRIP_ACTIVE;
        }
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
        TripMonitorAlertNotification notification = TripMonitorAlertNotification.createAlertNotification(
            previousAlerts,
            newAlerts,
            getOtpUserLocale()
        );
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
    public TripMonitorNotification checkTripForDelays() {
        if (journeyState.realtimeDataLost()) {
            // Reset baseline if real-time updates are lost.
            journeyState.baselineArrivalTimeEpochMillis = 0;
            journeyState.baselineDepartureTimeEpochMillis = 0;

            return getMinutesUntilTrip() <= trip.leadTimeInMinutes
                ? TripMonitorNotification.updatesLostNotification(getOtpUserLocale())
                : null;
        } else {
            Date newStartDate = matchingItinerary.startTime;
            Date newEndDate = matchingItinerary.endTime;
            long newStartTime = newStartDate.getTime();
            long newEndTime = newEndDate.getTime();

            // Fallback on scheduled if baseline is zero.
            long departureDelay = Math.abs(diffInMinutes(journeyState.tripDepartureTime(), newStartTime));
            long arrivalDelay = Math.abs(diffInMinutes(journeyState.tripArrivalTime(), newEndTime));

            // For each of the cases below, use the scheduled departure/arrival epoch millis of the trip
            // (the scheduled departure/arrival time if checking for departure/arrival delay, respectively).

            boolean isDepartureDelay = departureDelay >= trip.departureVarianceMinutesThreshold;
            boolean isArrivalDelay = arrivalDelay >= trip.arrivalVarianceMinutesThreshold;
            if (departureDelay == arrivalDelay && (isDepartureDelay || isArrivalDelay)) {
                // Do a combined departure/arrival delay notification.
                long delayMinutes = diffInMinutes(newStartTime, journeyState.scheduledDepartureTimeEpochMillis);
                journeyState.baselineDepartureTimeEpochMillis = newStartTime;
                journeyState.baselineArrivalTimeEpochMillis = newEndTime;
                return TripMonitorNotification.createDelayNotification(
                    delayMinutes,
                    newStartDate,
                    newEndDate,
                    NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                    getOtpUserLocale()
                );
            } else if (isDepartureDelay) {
                // Do a departure delay notification.
                long delayMinutes = diffInMinutes(newStartTime, journeyState.scheduledDepartureTimeEpochMillis);
                journeyState.baselineDepartureTimeEpochMillis = newStartTime;

                return TripMonitorNotification.createDelayNotification(
                    delayMinutes,
                    newStartDate,
                    newEndDate,
                    NotificationType.DEPARTURE_DELAY,
                    getOtpUserLocale()
                );
            } else if (isArrivalDelay) {
                // Do an arrival delay notification.
                long delayMinutes = diffInMinutes(newEndTime, journeyState.scheduledArrivalTimeEpochMillis);
                journeyState.baselineArrivalTimeEpochMillis = newEndTime;
                return TripMonitorNotification.createDelayNotification(
                    delayMinutes,
                    newStartDate,
                    newEndDate,
                    NotificationType.ARRIVAL_DELAY,
                    getOtpUserLocale()
                );
            }
            return null;
        }
    }

    /**
     * Send notification to user associated with the trip.
     */
    private void sendNotifications() {
        OtpUser otpUser = getOtpUser();
        if (otpUser == null) {
            LOG.error("Cannot find user for id {}", trip.userId);
            // TODO: Bugsnag / delete monitored trip?
            return;
        }
        sendNotifications(otpUser);
    }

    /**
     * Compose a message for any enqueued notifications and send to {@link OtpUser} based on their notification
     * preferences.
     */
    private void sendNotifications(OtpUser otpUser) {
        // Update push notification devices count, which may change asynchronously
        NotificationUtils.updatePushDevices(otpUser);

        boolean hasInitialReminder = initialReminderNotification != null;

        if (notifications.isEmpty() && !hasInitialReminder) {
            // FIXME: Change log level
            LOG.info("No notifications queued for trip. Skipping notify.");
            return;
        }
        // If the same notifications were just sent, there is no need to send the same notification.
        // TODO: Should there be some time threshold check here based on lastNotificationTime?
        if (thereAreNoNewNotifications() && !hasInitialReminder) {
            LOG.info("Last notifications match current ones. Skipping notify.");
            return;
        }

        Locale locale = I18nUtils.getOtpUserLocale(otpUser);
        String tripNameOrReminder = hasInitialReminder ? initialReminderNotification.body : trip.tripName;
        if (tripNameOrReminder == null) {
            tripNameOrReminder = Message.TRIP_NAME_UNDEFINED.get(locale);
        }

        // A HashMap is needed instead of a Map for template data to be serialized to the template renderer.
        Map<String, Object> templateData = new HashMap<>();
        templateData.putAll(Map.of(
            "emailGreeting", Message.TRIP_EMAIL_GREETING.get(locale),
            "tripNameOrReminder", tripNameOrReminder,
            "notifications", new ArrayList<>(notifications),
            "smsFooter", Message.SMS_STOP_NOTIFICATIONS.get(locale)
        ));
        templateData.putAll(NotificationUtils.getTripNotificationFields(trip, locale));
        if (hasInitialReminder) {
            templateData.put("initialReminder", initialReminderNotification);
        }
        // FIXME: Change log level
        LOG.info("Sending notification to user {}", trip.userId);
        boolean successEmail = false;
        boolean successPush = false;
        boolean successSms = false;

        if (otpUser.notificationChannel.contains(OtpUser.Notification.EMAIL)) {
            successEmail = sendEmail(otpUser, templateData, locale);
        }
        if (otpUser.notificationChannel.contains(OtpUser.Notification.PUSH)) {
            successPush = sendPush(otpUser, templateData);
        }
        if (otpUser.notificationChannel.contains(OtpUser.Notification.SMS)) {
            successSms = sendSMS(otpUser, templateData);
        }

        // TODO: better handle below when one of the following fails
        if (successEmail || successPush || successSms || IS_TEST) {
            notificationTimestampMillis = DateTimeUtils.currentTimeMillis();
        }
    }

    /**
     * Determines whether pending notifications are the same as notifications previously sent.
     */
    public boolean thereAreNoNewNotifications() {
        return previousJourneyState.lastNotifications.containsAll(notifications);
    }

    /**
     * Send notification SMS in MonitoredTrip template.
     */
    private boolean sendSMS(OtpUser otpUser, Map<String, Object> data) {
        return NotificationUtils.sendSMS(otpUser, "MonitoredTripSms.ftl", data) != null;
    }

    /**
     * Send push notification.
     */
    private boolean sendPush(OtpUser otpUser, Map<String, Object> data) {
        return NotificationUtils.sendPush(otpUser, "MonitoredTripPush.ftl", data, this.trip.tripName, this.trip.id) != null;
    }

    /**
     * Send notification email in MonitoredTrip template.
     */
    private boolean sendEmail(OtpUser otpUser, Map<String, Object> data, Locale locale) {
        String subject = NotificationUtils.getTripEmailSubject(otpUser, locale, trip);
        return NotificationUtils.sendEmail(
            otpUser,
            subject,
            "MonitoredTripText.ftl",
            "MonitoredTripHtml.ftl",
            data
        );
    }

    private void enqueueNotification(TripMonitorNotification ...tripMonitorNotifications) {
        for (TripMonitorNotification notification : tripMonitorNotifications) {
            if (notification != null) notifications.add(notification);
        }
    }

    /**
     * Define the number of minutes until the start of a trip. If dealing with a one time trip, the matching itinerary
     * is unlikely to be defined in which case use the original trip start time instead.
     */
    private long getMinutesUntilTrip() {
        // Get current time and trip time (with the time offset to today) for comparison.
        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();

        Instant tripStartInstant = !trip.isOneTime() && !isPreviousTripOngoingAtLastCheck()
            ? findEarliestTargetDate(trip, now).toInstant()
            : matchingItinerary.startTime.toInstant();

        return (tripStartInstant.getEpochSecond() - now.toEpochSecond()) / 60;
    }

    private long getMinutesSinceLastCheck() {
        long millisSinceLastCheck = DateTimeUtils.currentTimeMillis() - previousJourneyState.lastCheckedEpochMillis;
        return TimeUnit.MILLISECONDS.toMinutes(millisSinceLastCheck);
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
        return shouldSkipMonitoredTripCheck(true);
    }

    public boolean shouldSkipMonitoredTripCheck(boolean persist) throws Exception {
        // before anything else, return true if the trip is inactive
        if (!trip.isActive) {
            LOG.info("Skipping: Trip is inactive.");
            return true;
        }

        // If trip is no longer possible, no further checking is needed. The itinerary existence data should not be
        // checked here to avoid incorrectly skipping trips that are monitored on a single day of the week, but which
        // may have not had a matching itinerary on that day for one week (even though the trip could be possible the
        // next week).
        if (previousJourneyState.tripStatus == TripStatus.NO_LONGER_POSSIBLE) {
            LOG.info("Skipping: Trip is no longer possible.");
            return true;
        }

        // If trip is one-time and has ended, no further checking is needed. The itinerary existence data should not be
        // checked here to avoid incorrectly skipping trips that are monitored on a single day of the week, but which
        // may have not had a matching itinerary on that day for one week (even though the trip could be possible the
        // next week).
        if (isOneTimeTripInPast()) {
            LOG.info("Skipping: One-time trip is in the past.");
            return true;
        }

        // For trips that are snoozed, see if they should be unsnoozed first.
        if (trip.snoozed) {
            if (shouldUnsnoozeTrip()) {
                // Clear previous matching itinerary as we want to start afresh.
                previousMatchingItinerary = null;
                // unsnooze trip now, for cases where the next itinerary isn't calculated
                trip.snoozed = false;
            } else {
                LOG.info("Skipping: Trip is snoozed.");
                return true;
            }
        }

        // initialize the trip's journey state and matching itinerary to the latest journeyState's matching
        // itinerary, or use the itinerary that the trip was saved with
        if (previousMatchingItinerary == null) {
            // clone the trip's itinerary just in case the code attempts to save the trip (and thus the itinerary)
            matchingItinerary = trip.itinerary.clone();
        } else {
            matchingItinerary = previousMatchingItinerary;
        }

        computeTargetZonedDateTime();
        updateTripStatus();

        if (isPreviousTripOngoingAtLastCheck()) {
            matchingItinerary = previousMatchingItinerary;

            // Skip checking the trip the rest of the time that it is active if the trip was deemed not possible for the
            // next possible time during a previous query to find candidate itinerary matches.
            if (previousJourneyState.tripStatus == TripStatus.NEXT_TRIP_NOT_POSSIBLE) {
                LOG.info("Skipping: Next trip was not found.");
                return true;
            }
        } else {
            // save journey state with updated matching itinerary and target date
            if (persist && !updateMonitoredTrip()) {
                LOG.info("Skipping: Trip no longer exists in Mongo.");
                return true;
            }
        }

        // If last check was more than an hour ago and trip doesn't occur until an hour from now, check trip.
        long minutesSinceLastCheck = getMinutesSinceLastCheck();
        LOG.info("{} minutes since last checking trip", minutesSinceLastCheck);
        long minutesUntilTrip = getMinutesUntilTrip();
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
                LOG.debug("Trip not checked in at least an {} minutes. Checking.", overHourCheckThresholdMinutes);
                return false;
            }
        } else {
            // It's less than an hour until the trip time, start more frequent trip checks (about every 15 minutes).
            if (minutesSinceLastCheck >= 15) {
                // Last check was more than 15 minutes ago. Check. (approx. 4 checks per hour).
                LOG.debug("Trip happening soon. Checking.");
                return false;
            }
            // If the trip starts within 30 minutes, check the trip every minute (assuming the loop runs every minute).
            int checkEveryMinuteThresholdMinutes = 30;
            if (minutesUntilTrip <= checkEveryMinuteThresholdMinutes) {
                LOG.debug("Trip happening within {} minutes. Checking every minute.", checkEveryMinuteThresholdMinutes);
                return false;
            }
        }
        // TODO: Check that journey state is not flagged
        // TODO: Check last notification time.
        // Default to skipping check.
        LOG.debug("Trip criteria not met to check. Skipping.");
        return true;
    }

    /**
     * Calculate target time for the next trip plan request. Find the next possible day the trip is active by
     * initializing the appropriate target time.
     */
    private void computeTargetZonedDateTime() {
        targetZonedDateTime = matchingItinerary.isActive()
            ? DateTimeUtils.makeOtpZonedDateTime(matchingItinerary.startTime)
            : findEarliestTargetDate(trip, DateTimeUtils.nowAsZonedDateTime());
    }

    private boolean isPreviousTripOngoingAtLastCheck() {
        return isPrevMatchingItineraryNotConcludedAtLastCheck() && isPrevMatchingItineraryDayValid();
    }

    /**
     * Find, starting from the given date, the earliest target date for a monitored trip,
     * using the trip start time and the monitored days.
     * (Itinerary existence is not being checked, assuming that clients prevent monitoring days when a trip doesn't exist.)
     */
    public static ZonedDateTime findEarliestTargetDate(MonitoredTrip trip, ZonedDateTime fromDateTime) {
        ZonedDateTime itineraryEndTimeToday = makeOtpZonedDateTime(
            fromDateTime,
            trip.itinerary.endTime.toInstant()
        );

        int daysToAdd = fromDateTime.toInstant().isAfter(itineraryEndTimeToday.toInstant()) ? 1 : 0;

        ZonedDateTime nextStartDay = makeOtpZonedDateTime(
            fromDateTime.plusDays(daysToAdd),
            trip.itinerary.startTime.toInstant()
        );

        return findNextMonitoredDay(trip, nextStartDay);
    }

    /**
     * Advance the target date/time until a day is found when the trip is active.
     */
    private static ZonedDateTime findNextMonitoredDay(MonitoredTrip trip, ZonedDateTime startingDay) {
        ZonedDateTime nextMonitoredDay = startingDay;
        if (!trip.isOneTime()) {
            while (!trip.isActiveOnDate(nextMonitoredDay)) {
                nextMonitoredDay = nextMonitoredDay.plusDays(1);
            }
        }

        return nextMonitoredDay;
    }

    /**
     * Is a one-off trip which has already happened.
     */
    private boolean isOneTimeTripInPast() {
        return trip.isOneTime() && previousJourneyState.tripStatus == TripStatus.PAST_TRIP;
    }

    /** Check if previous matching itinerary day is still valid */
    private boolean isPrevMatchingItineraryDayValid() {
        if (previousMatchingItinerary == null) return false;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(previousMatchingItinerary.startTime);

        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.SUNDAY:
                return trip.sunday;
            case Calendar.MONDAY:
                return trip.monday;
            case Calendar.TUESDAY:
                return trip.tuesday;
            case Calendar.WEDNESDAY:
                return trip.wednesday;
            case Calendar.THURSDAY:
                return trip.thursday;
            case Calendar.FRIDAY:
                return trip.friday;
            case Calendar.SATURDAY:
                return trip.saturday;
            default:
                return false; // This should never happen, but for safety
        }
    }

    /** Check if the previous matching itinerary was null or if it has already concluded */
    private boolean isPrevMatchingItineraryNotConcludedAtLastCheck() {
        if (previousMatchingItinerary == null) return false;
        Date lastCheckedDate = new Date(previousJourneyState.lastCheckedEpochMillis);
        return previousMatchingItinerary.endTime.after(lastCheckedDate) &&
            previousMatchingItinerary.startTime.before(lastCheckedDate);
    }

    /**
     * Sets the journey state scheduled time based on the monitored itinerary (subtracting any delays),
     * and applying offsets corresponding to the number of days between "now" and the target date.
     */
    private void resetJourneyState() {
        long millis = trip.itinerary.startTime.toInstant().until(targetZonedDateTime, ChronoUnit.MILLIS);
        journeyState.scheduledDepartureTimeEpochMillis = trip.itinerary.getScheduledStartTimeEpochMillis() + millis;
        journeyState.scheduledArrivalTimeEpochMillis = trip.itinerary.getScheduledEndTimeEpochMillis() + millis;
        journeyState.hasRealtimeData = false;
    }

    /**
     * Update the monitored trip with the updated journey state with updated matching itinerary and target date. Returns
     * false if the update was unsuccessful due to the trip no longer existing in the database.
     */
    private boolean updateMonitoredTrip() {
        // make sure the trip still exists before saving it. It is possible that the user deleted the trip after this
        // job started but before this database update.
        if (Persistence.monitoredTrips.getById(trip.id) == null) {
            // trip has been deleted!
            return false;
        }
        journeyState.matchingItinerary = matchingItinerary;
        if (targetZonedDateTime != null) {
            journeyState.targetDate = targetZonedDateTime.format(DEFAULT_DATE_FORMATTER);
        }
        journeyState.lastCheckedEpochMillis = DateTimeUtils.currentTimeMillis();
        // Update notification time if notification successfully sent.
        if (notificationTimestampMillis != -1) {
            journeyState.lastNotificationTimeMillis = notificationTimestampMillis;
            // Prevent repeated notifications by saving successfully sent notifications.
            journeyState.lastNotifications.addAll(notifications);
        }
        trip.journeyState = journeyState;
        Persistence.monitoredTrips.replace(trip.id, trip);
        return true;
    }

    /**
     * Whether to keep the matching itinerary.
     * @return true if matchingItinerary and target date are the same,
     * or trip is one-time and matchingItinerary occurs on the trip date
     * (assuming matchingItinerary is not null).
     */
    private boolean shouldPersistMatchingItinerary() {
        if (matchingItinerary == null) return false;
        String matchingItineraryDay = makeOtpZonedDateTime(matchingItinerary.startTime).format(DEFAULT_DATE_FORMATTER);

        if (trip.isOneTime() && makeOtpZonedDateTime(trip.itinerary.startTime).format(DEFAULT_DATE_FORMATTER).equals(matchingItineraryDay)) return true;
        return targetZonedDateTime != null && targetZonedDateTime.format(DEFAULT_DATE_FORMATTER).equals(matchingItineraryDay);
    }

    /**
     * Retrieves and caches the user on first call (assuming the user for a trip does not change during a trip check).
     */
    private OtpUser getOtpUser() {
        if (!userChecked) {
            cachedUser = Persistence.otpUsers.getById(trip.userId);
            userChecked = true;
        }
        return cachedUser;
    }

    /**
     * Retrieves and caches the user on first call (assuming the user for a trip does not change).
     */
    private Locale getOtpUserLocale() {
        return I18nUtils.getOtpUserLocale(getOtpUser());
    }

    /**
     * Whether a trip should be unsnoozed and monitoring should resume.
     * @return true if the current time is after the calendar day (on or after midnight)
     * after the matching trip start day, false otherwise.
     */
    public boolean shouldUnsnoozeTrip() {
        ZoneId otpZoneId = DateTimeUtils.getOtpZoneId();
        var midnightAfterLastChecked = ZonedDateTime
            .ofInstant(
                Instant.ofEpochMilli(previousJourneyState.lastCheckedEpochMillis).plus(1, ChronoUnit.DAYS),
                otpZoneId
            )
            .withHour(0)
            .withMinute(0)
            .withSecond(0);

        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        // Include equal or after midnight as true.
        return !now.isBefore(midnightAfterLastChecked);
    }
}
