package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
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

    /**
     * The {@link MonitoredTrip} id that this journey state is tracking.
     */
    public String monitoredTripId;

    /**
     * User ID for {@link OtpUser} that owns the {@link MonitoredTrip}.
     */
    private String userId;

    /**
     * Timestamp checking the last time a journey was checked.
     */
    public long lastChecked;

    /**
     * Store the recent plan requests made over the course of monitoring a trip. Note: these should be cleared once the
     * monitored trip clears for the day (i.e., if the monitored trip occurs at 9am, responses will stack up as we check
     * the trip. At 9:01am (or perhaps some later time in the day) this should be cleared.).
     *
     * FIXME: Should the type be string/responseBody instead?
     */
    public Response lastResponse;

    public int matchingItineraryIndex;

    public Set<TripMonitorNotification> lastNotifications = new HashSet<>();

    public long lastNotificationTime;

    public Set<LocalizedAlert> lastSeenAlerts = new HashSet<>();

    public int lastDepartureDelay;

    public int lastArrivalDelay;

    /**
     * Update journey state based on results from {@link CheckMonitoredTrip}.
     * TODO: This may need some tweaking depending on whether a check was successfully completed or not.
     *   E.g., should a previous journey state be overwritten by a failed check?
     */
    public void update(CheckMonitoredTrip checkMonitoredTripJob) {
        this.lastChecked = DateTimeUtils.currentTimeMillis();
        this.matchingItineraryIndex = checkMonitoredTripJob.matchingItineraryIndex;
        this.lastResponse = checkMonitoredTripJob.otpResponse;
        this.lastDepartureDelay = checkMonitoredTripJob.departureDelay;
        this.lastArrivalDelay = checkMonitoredTripJob.arrivalDelay;
        // Update notification time if notification successfully sent.
        if (checkMonitoredTripJob.notificationTimestamp != -1) {
            this.lastNotificationTime = checkMonitoredTripJob.notificationTimestamp;
        }
        Persistence.journeyStates.replace(this.id, this);
    }
}
