package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
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

    public int lastArrivalDelay;

    public int lastDepartureDelay;

    /**
     * Timestamp checking the last time a journey was checked.
     */
    public long lastChecked;

    public Set<TripMonitorNotification> lastNotifications = new HashSet<>();

    public long lastNotificationTime;

    public Set<LocalizedAlert> lastSeenAlerts = new HashSet<>();

    /**
     * The current or upcoming matching itinerary from plan requests made over the course of monitoring a trip.
     */
    public Itinerary matchingItinerary;

    /**
     * The {@link MonitoredTrip} id that this journey state is tracking.
     */
    public String monitoredTripId;

    public String targetDate;

    /**
     * User ID for {@link OtpUser} that owns the {@link MonitoredTrip}.
     */
    private String userId;

    /**
     * Update journey state based on results from {@link CheckMonitoredTrip}.
     * TODO: This may need some tweaking depending on whether a check was successfully completed or not.
     *   E.g., should a previous journey state be overwritten by a failed check?
     */
    public void update(CheckMonitoredTrip checkMonitoredTripJob) {
        targetDate = checkMonitoredTripJob.targetDate;
        lastChecked = DateTimeUtils.currentTimeMillis();
        matchingItinerary = checkMonitoredTripJob.matchingItinerary;
        lastDepartureDelay = checkMonitoredTripJob.departureDelay;
        lastArrivalDelay = checkMonitoredTripJob.arrivalDelay;
        // Update notification time if notification successfully sent.
        if (checkMonitoredTripJob.notificationTimestamp != -1) {
            lastNotificationTime = checkMonitoredTripJob.notificationTimestamp;
        }
        Persistence.journeyStates.replace(this.id, this);
    }
}
