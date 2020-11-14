package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripMonitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks information during the active monitoring of a {@link org.opentripplanner.middleware.models.MonitoredTrip}
 * (e.g., last alerts encountered, last time a check was made, etc.).
 */
public class JourneyState extends Model {
    /**
     * The current arrival/departure baseline to use when checking if a new threshold has been met
     */
    public long baselineArrivalTimeEpochMillis;
    public long baselineDepartureTimeEpochMillis;

    /**
     * The original arrival/departure time of the trip in a scheduled state.
     */
    public long originalArrivalTimeEpochMillis;
    public long originalDepartureTimeEpochMillis;

    /**
     * Timestamp checking the last time a journey was checked.
     */
    public long lastCheckedEpochMillis;

    /**
     * The notifications already sent.
     * FIXME this is never set, so it has no effect.
     */
    public Set<TripMonitorNotification> lastNotifications = new HashSet<>();

    /**
     * The last time a notification was sent.
     * FIXME this is never accessed anywhere and might not be worth persisting.
     */
    public long lastNotificationTimeMillis;

    /**
     * The current or upcoming matching itinerary from plan requests made over the course of monitoring a trip.
     */
    public Itinerary matchingItinerary;

    /**
     * The {@link MonitoredTrip} id that this journey state is tracking.
     */
    public String monitoredTripId;

    /**
     * The current targetDate for which the trip is being monitored. This can be either a trip currently happening or
     * the next possible date a monitored trip would occur.
     */
    public String targetDate;

    public TripStatus tripStatus;

    /**
     * User ID for {@link OtpUser} that owns the {@link MonitoredTrip}.
     */
    private String userId;

    public boolean noLongerPossible = false;

    /**
     * No-arg constructor for de-serialization.
     */
    public JourneyState() {
    }

    /**
     * Main constructor to create journey state for associated {@link MonitoredTrip}.
     */
    public JourneyState(MonitoredTrip monitoredTrip) {
        this.monitoredTripId = monitoredTrip.id;
        this.userId = monitoredTrip.userId;
    }

    /**
     * Update journey state based on results from {@link CheckMonitoredTrip}.
     * TODO: This may need some tweaking depending on whether a check was successfully completed or not.
     *   E.g., should a previous journey state be overwritten by a failed check?
     */
    public void update(CheckMonitoredTrip checkMonitoredTripJob) {
        targetDate = checkMonitoredTripJob.targetDate;
        lastCheckedEpochMillis = DateTimeUtils.currentTimeMillis();
        matchingItinerary = checkMonitoredTripJob.matchingItinerary;
        // Update notification time if notification successfully sent.
        if (checkMonitoredTripJob.notificationTimestampMillis != -1) {
            lastNotificationTimeMillis = checkMonitoredTripJob.notificationTimestampMillis;
        }
        Persistence.journeyStates.replace(this.id, this);
    }
}
