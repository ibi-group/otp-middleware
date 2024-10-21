package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackedJourney extends Model {

    public String tripId;

    public String endCondition;

    public Date startTime;

    public Date endTime;

    public List<TrackingLocation> locations = new ArrayList<>();

    public Map<String, String> busNotificationMessages = new HashMap<>();

    public int longestConsecutiveDeviatedPoints = -1;

    public transient MonitoredTrip trip;

    public static final String TRIP_ID_FIELD_NAME = "tripId";

    public static final String LOCATIONS_FIELD_NAME = "locations";
    public static final String BUS_NOTIFICATION_MESSAGES_FIELD_NAME = "busNotificationMessages";

    public static final String END_TIME_FIELD_NAME = "endTime";

    public static final String END_CONDITION_FIELD_NAME = "endCondition";

    public static final String LONGEST_CONSECUTIVE_DEVIATED_POINTS_FIELD_NAME = "longestConsecutiveDeviatedPoints";

    public static final String TERMINATED_BY_USER = "Tracking terminated by user.";

    public static final String FORCIBLY_TERMINATED = "Tracking forcibly terminated.";

    public TrackedJourney() {
    }

    public TrackedJourney(String tripId, TrackingLocation location) {
        startTime = new Date();
        locations.add(location);
        this.tripId = tripId;
    }

    /**
     * Remove duplicates before adding to tracked locations.
     */
    public void update(List<TrackingLocation> locations) {
        this.locations.addAll(locations);
    }

    public void end(boolean isForciblyEnded) {
        this.endTime = new Date();
        this.endCondition = (isForciblyEnded) ? FORCIBLY_TERMINATED : TERMINATED_BY_USER;
    }

    @Override
    public boolean delete() {
        return Persistence.trackedJourneys.removeById(this.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TrackedJourney that = (TrackedJourney) o;
        return Objects.equals(tripId, that.tripId) && Objects.equals(startTime, that.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tripId, startTime);
    }

    public TrackingLocation lastLocation() {
        return locations.get(locations.size() - 1);
    }

    public void updateNotificationMessage(String routeId, String body) {
        busNotificationMessages.put(routeId, body);
        Persistence.trackedJourneys.updateField(
            id,
            BUS_NOTIFICATION_MESSAGES_FIELD_NAME,
            busNotificationMessages
        );
    }

    /** The sum of the deviations for all tracking locations that have it. */
    public double computeTotalDeviation() {
        if (locations == null) return -1;

        return locations.stream()
            .filter(l -> l.deviationMeters != null)
            .map(l -> l.deviationMeters)
            .reduce(0.0, Double::sum);
    }

    /** The largest consecutive deviations for all tracking locations marked "deviated". */
    public int computeLargestConsecutiveDeviations() {
        if (locations == null) return -1;

        int count = 0;
        int maxCount = 0;
        for (TrackingLocation location : locations) {
            // A trip status must have been computed for a location to count.
            // (The mobile app will send many other more for reference, but only those for which we compute a status
            // (i.e. the last coordinate in every batch) will potentially count.
            if (location.tripStatus != null) {
                // Traveler must be moving (speed != 0) for a deviated location to be counted.
                if (location.tripStatus == TripStatus.DEVIATED) {
                    if (location.speed != 0) {
                        count++;
                        if (maxCount < count) maxCount = count;
                    }
                } else {
                    // If a location has a status computed and is not deviated, reset the streak.
                    count = 0;
                }
            }
        }
        return maxCount;
    }
}
