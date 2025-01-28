package org.opentripplanner.middleware.triptracker;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.models.TripSurveyNotification;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.controllers.api.ApiController.ID_FIELD_NAME;
import static org.opentripplanner.middleware.models.MonitoredTrip.USER_ID_FIELD_NAME;
import static org.opentripplanner.middleware.models.OtpUser.TRIP_SURVEY_NOTIFICATIONS_FIELD;
import static org.opentripplanner.middleware.models.TrackedJourney.END_CONDITION_FIELD_NAME;
import static org.opentripplanner.middleware.models.TrackedJourney.END_TIME_FIELD_NAME;
import static org.opentripplanner.middleware.models.TrackedJourney.FORCIBLY_TERMINATED;
import static org.opentripplanner.middleware.models.TrackedJourney.TERMINATED_BY_USER;
import static org.opentripplanner.middleware.models.TripSurveyNotification.TIME_SENT_FIELD;
import static org.opentripplanner.middleware.triptracker.ManageTripTracking.TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveySenderJob implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveySenderJob.class);

    public static final int CONSECUTIVE_DEVIATIONS_WINDOW_SECONDS
        = getConfigPropertyAsInt("CONSECUTIVE_DEVIATIONS_WINDOW_SECONDS", 30);

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        LOG.info("TripSurveySenderJob started");

        // Pick users for which the last survey notification was sent more than a week ago.
        List<OtpUser> usersWithNotificationsOverAWeekAgo = getUsersWithNotificationsOverAWeekAgo();

        // Collect journeys that were completed/terminated in the past hour (skip ongoing journeys).
        List<TrackedJourney> journeysCompletedInPastHour = getCompletedJourneysInPastHour();

        // Map users to journeys.
        Map<OtpUser, List<TrackedJourney>> usersToJourneys = mapJourneysToUsers(journeysCompletedInPastHour, usersWithNotificationsOverAWeekAgo);

        for (Map.Entry<OtpUser, List<TrackedJourney>> entry : usersToJourneys.entrySet()) {
            // Find journey with the largest total deviation.
            Optional<TrackedJourney> optJourney = selectMostDeviatedJourneyUsingDeviatedPoints(entry.getValue());
            if (optJourney.isPresent()) {
                // Send push notification about that journey.
                MonitoredTrip trip = optJourney.get().trip;
                LOG.info("Sending survey notification for trip {}", trip.id);
                OtpUser otpUser = entry.getKey();
                String notificationId = UUID.randomUUID().toString();
                String pushResult = NotificationUtils.sendTripSurveyPush(otpUser, trip, notificationId);
                if (pushResult != null) {
                    // Store time of last sent survey notification for user.
                    otpUser.tripSurveyNotifications.add(new TripSurveyNotification(notificationId, new Date(), optJourney.get().id));
                    Persistence.otpUsers.updateField(otpUser.id, TRIP_SURVEY_NOTIFICATIONS_FIELD, otpUser.tripSurveyNotifications);
                } else {
                    LOG.warn("Could not send survey notification for trip {}", trip.id);
                }

            }
        }

        LOG.info("TripSurveySenderJob completed in {} sec", (System.currentTimeMillis() - start) / 1000);
    }

    /**
     * Get users whose last trip survey notification was at least a week ago.
     */
    public static List<OtpUser> getUsersWithNotificationsOverAWeekAgo() {
        Date aWeekAgo = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));

        // If TRIP_SURVEY_NOTIFICATIONS_FIELD is not empty, users notified a week ago would have:
        // - at least one entry made a week ago, and
        // - zero entries made less than a week ago.
        Bson dateFilter = Filters.and(
            Filters.elemMatch(TRIP_SURVEY_NOTIFICATIONS_FIELD, Filters.lte(TIME_SENT_FIELD, aWeekAgo)),
            Filters.not(Filters.elemMatch(TRIP_SURVEY_NOTIFICATIONS_FIELD, Filters.gt(TIME_SENT_FIELD, aWeekAgo)))
        );

        Bson surveyNotSentFilter = Filters.or(
            Filters.not(Filters.exists(TRIP_SURVEY_NOTIFICATIONS_FIELD)),
            Filters.size(TRIP_SURVEY_NOTIFICATIONS_FIELD, 0)
        );
        Bson overallFilter = Filters.or(dateFilter, surveyNotSentFilter);

        return Persistence.otpUsers.getFiltered(overallFilter).into(new ArrayList<>());
    }

    /**
     * Gets tracked journeys for all users that were completed in the past hour.
     */
    public static List<TrackedJourney> getCompletedJourneysInPastHour() {
        Date now = new Date();
        Date oneHourAgo = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Bson dateFilter = Filters.and(
            Filters.gte(END_TIME_FIELD_NAME, oneHourAgo),
            Filters.lte(END_TIME_FIELD_NAME, now)
        );
        Bson completeFilter = Filters.eq(END_CONDITION_FIELD_NAME, TERMINATED_BY_USER);
        Bson terminatedFilter = Filters.eq(END_CONDITION_FIELD_NAME, FORCIBLY_TERMINATED);
        Bson overallFilter = Filters.and(dateFilter, Filters.or(completeFilter, terminatedFilter));

        return Persistence.trackedJourneys.getFiltered(overallFilter).into(new ArrayList<>());
    }

    /**
     * Gets the trips for the given journeys and users.
     */
    public static List<MonitoredTrip> getTripsForJourneysAndUsers(List<TrackedJourney> journeys, List<OtpUser> otpUsers) {
        Set<String> tripIds = journeys.stream().map(j -> j.tripId).collect(Collectors.toSet());
        Set<String> userIds = otpUsers.stream().map(u -> u.id).collect(Collectors.toSet());

        Bson tripIdFilter = Filters.in(ID_FIELD_NAME, tripIds);
        Bson userIdFilter = Filters.in(USER_ID_FIELD_NAME, userIds);
        Bson overallFilter = Filters.and(tripIdFilter, userIdFilter);

        return Persistence.monitoredTrips.getFiltered(overallFilter).into(new ArrayList<>());
    }

    /**
     * Map journeys to users.
     */
    public static Map<OtpUser, List<TrackedJourney>> mapJourneysToUsers(List<TrackedJourney> journeys, List<OtpUser> otpUsers) {
        List<MonitoredTrip> trips = getTripsForJourneysAndUsers(journeys, otpUsers);

        Map<String, OtpUser> userMap = otpUsers.stream().collect(Collectors.toMap(u -> u.id, Function.identity()));

        HashMap<OtpUser, List<TrackedJourney>> map = new HashMap<>();
        for (MonitoredTrip trip : trips) {
            List<TrackedJourney> journeyList = map.computeIfAbsent(userMap.get(trip.userId), u -> new ArrayList<>());
            for (TrackedJourney journey : journeys) {
                if (trip.id.equals(journey.tripId)) {
                    journey.trip = trip;
                    journeyList.add(journey);
                }
            }
        }

        return map;
    }

    public static Optional<TrackedJourney> selectMostDeviatedJourneyUsingDeviatedPoints(List<TrackedJourney> journeys) {
        if (journeys == null) return Optional.empty();
        final double consecutiveDeviationsThreshold = Math.ceil((double) CONSECUTIVE_DEVIATIONS_WINDOW_SECONDS / TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS);
        return journeys
            .stream()
            .filter(j -> j.longestConsecutiveDeviatedPoints >= consecutiveDeviationsThreshold)
            .max(Comparator.comparingInt(j -> j.longestConsecutiveDeviatedPoints));
    }
}
