package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Response;

import java.util.ArrayList;
import java.util.List;

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
    public List<Response> responses = new ArrayList<>();
}
