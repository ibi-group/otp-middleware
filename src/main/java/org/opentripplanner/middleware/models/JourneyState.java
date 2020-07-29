package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the state of a {@link MonitoredTrip} while it is actively being monitored.
 */
public class JourneyState extends Model {

    public JourneyState() {
    }

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
