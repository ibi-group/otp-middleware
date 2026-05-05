package org.opentripplanner.middleware.tripmonitor.jobs;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
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
        unsnoozeTripsAsNeeded();
        LOG.info("UnsnoozeTripsJob completed in {} sec", (System.currentTimeMillis() - start) / 1000);
    }

    /**
     * Extracts trips to be unsnoozed from a list of trips.
     */
    public static List<MonitoredTrip> getTripsToUnsnooze(List<MonitoredTrip> trips) {
        return trips
            .stream()
            .filter(t -> shouldUnsnooze(t.journeyState.lastCheckedEpochMillis))
            .collect(Collectors.toList());
    }

    /**
     * Get snoozed trips. This will populate only select fields into PartialTrip to limit save on bandwidth.
     */
    public static List<MonitoredTrip> getSnoozedTrips() {
        // Get active snoozed trips.
        Bson filter = Filters.and(
            Filters.eq("isActive", true),
            Filters.eq("snoozed", true)
        );
        return Persistence.monitoredTrips.getFiltered(filter).into(new ArrayList<>());
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

    private static void unsnoozeTripsAsNeeded() {
        for (MonitoredTrip trip : getTripsToUnsnooze(getSnoozedTrips())) {
            try {
                trip.recomputeTargetDateAndAdjustMatchingItinerary();
                trip.snoozed = false;
                Persistence.monitoredTrips.replace(trip.id, trip);
            } catch (CloneNotSupportedException e) {
                LOG.warn("Error unsnoozing trip {}", trip.id);
            }
        }
    }

    @Override
    public void scheduleRecurringJob() {
        // Already scheduled with MonitorAllTripsJob,
        // to avoid adding a command-line parameter and because both are not really separable.
    }
}
