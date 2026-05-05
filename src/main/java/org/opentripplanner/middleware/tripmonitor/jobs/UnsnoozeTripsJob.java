package org.opentripplanner.middleware.tripmonitor.jobs;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.recurringjobs.RecurringJobScheduler;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This job will unsnooze applicable snoozed monitored trips.
 */
public class UnsnoozeTripsJob implements Runnable, RecurringJobScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(UnsnoozeTripsJob.class);

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        LOG.info("UnsnoozeTripsJob started");
        // Unsnooze all trips
        unsnoozeTripsAsNeeded();

        LOG.info("UnsnoozeTripsJob completed in {} sec", (System.currentTimeMillis() - start) / 1000);
    }

    /**
     * Extracts trips to be unsnoozed from a list of trips.
     */
    public static List<PartialTrip> getTripsToUnsnooze(List<PartialTrip> trips) {
        return trips
            .stream()
            .filter(t -> shouldUnsnooze(t.journeyState.lastCheckedEpochMillis))
            .collect(Collectors.toList());
    }

    public static Set<String> getTripIdsToUnsnooze() {
        return getTripIds(getTripsToUnsnooze(getSnoozedTrips()));
    }

    /**
     * Get snoozed trips. This will populate only select fields to save on computation and bandwidth.
     */
    public static List<PartialTrip> getSnoozedTrips() {
        // Get active snoozed trips.
        Bson filter = Filters.and(
            Filters.eq("isActive", true),
            Filters.eq("snoozed", true)
        );
        return Persistence.tripsByLastChecked.getFiltered(filter).into(new ArrayList<>());
    }

    /**
     * Whether a trip should be unsnoozed and monitoring should resume.
     * @return true if the current time is on the calendar day (on or after midnight) after the last checked time.
     */
    private static boolean shouldUnsnooze(long millis) {
        var midnightAfterLastChecked = ZonedDateTime
            .ofInstant(
                Instant.ofEpochMilli(millis).plus(1, ChronoUnit.DAYS),
                DateTimeUtils.getOtpZoneId()
            )
            .truncatedTo(ChronoUnit.DAYS);

        ZonedDateTime now = DateTimeUtils.nowAsZonedDateTime();
        // Include equal or after midnight as true.
        return !now.isBefore(midnightAfterLastChecked);
    }

    public static void unsnoozeTripsAsNeeded() {
        Bson tripFilter = Filters.in("_id", getTripIdsToUnsnooze());
        for (MonitoredTrip trip : Persistence.monitoredTrips.getFiltered(tripFilter)) {
            try {
                trip.recomputeTargetDateAndAdjustMatchingItinerary();
                trip.snoozed = false;
                Persistence.monitoredTrips.replace(trip.id, trip);
            } catch (CloneNotSupportedException e) {
                LOG.warn("Error unsnoozing trip {}", trip.id);
            }
        }
    }

    public static Set<String> getTripIds(List<PartialTrip> trips) {
        return trips.stream().map(t -> t.id).collect(Collectors.toSet());
    }

    @Override
    public void scheduleRecurringJob() {
        // Already scheduled with MonitorAllTripsJob,
        // to avoid adding a command-line parameter and because both are not really separable.
    }

    /**
     * Helper class with a subset of {@link org.opentripplanner.middleware.tripmonitor.JourneyState} fields.
     */
    public static class PartialJourneyState {
        public long lastCheckedEpochMillis;
    }

    /**
     * Helper class with a subset of {@link MonitoredTrip} fields.
     */
    public static class PartialTrip extends Model {
        public PartialJourneyState journeyState;
        // fetched and isActive are control fields for tests.
        public boolean snoozed;
        public boolean isActive;
    }
}
