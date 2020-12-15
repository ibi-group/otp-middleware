package org.opentripplanner.middleware.tripmonitor;

import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks information during the active monitoring of a {@link org.opentripplanner.middleware.models.MonitoredTrip}
 * (e.g., last alerts encountered, last time a check was made, etc.).
 */
public class JourneyState implements Cloneable {
    /**
     * The current arrival/departure baseline to use when checking if a new threshold has been met for the active or
     * upcoming itinerary. These values are updated whenever a notification has already been sent out that informed the
     * user that the trip's estimated departure or arrival time changed. Subsequent comparisons will then check against
     * this updated baseline to determine if this new threshold for sending an alert has been met.
     *
     * For example, if a trip's departure was delayed by 20 minutes and the user had a delay threshold of 15 minutes
     * set, then the baselineArrivalTimeEpochMillis would be updated to reflect this 20 minute delay and further checks
     * for arrival delay will be relative to this time. So a new departure alert would get generated if the delay went
     * below 5 minutes or above 35 minutes.
     */
    public long baselineArrivalTimeEpochMillis;
    public long baselineDepartureTimeEpochMillis;

    /**
     * The arrival/departure times of the trip in a scheduled state for the active or upcoming itinerary. In other
     * words, these start and end times are what would be expected if the itinerary were exactly on-time.
     */
    public long scheduledArrivalTimeEpochMillis;
    public long scheduledDepartureTimeEpochMillis;

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
     * The current targetDate for which the trip is being monitored. This can be either a trip currently happening or
     * the next possible date a monitored trip would occur.
     */
    public String targetDate;

    /**
     * The overall status of the trip. This gets set in the {@link CheckMonitoredTrip} job.
     */
    public TripStatus tripStatus;

    public JourneyState() {}

    /**
     * Clone this object.
     * NOTE: This is only clones certain needed items so not all entities are deep-cloned. Implement this further if
     * additional items should be deep-cloned.
     */
    @Override
    public JourneyState clone() throws CloneNotSupportedException {
        JourneyState cloned = (JourneyState) super.clone();
        if (matchingItinerary != null) {
            cloned.matchingItinerary = matchingItinerary.clone();
        }
        return cloned;
    }
}
