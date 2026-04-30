package org.opentripplanner.middleware.tripmonitor.jobs;

import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.recurringjobs.RecurringJobScheduler;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * This job will analyze applicable monitored trips and create further individual tasks to analyze each individual trip.
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
     * Filter to get trips that are active and snoozed.
     */
    public static Bson makeActiveSnoozedTripsFilter() {
        return and(
            eq("isActive", true),
            eq("snoozed", true)
        );
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

    public static List<MonitoredTrip> getTripsToUnsnooze() {
        // Get active snoozed trips.
        Bson filter = UnsnoozeTripsJob.makeActiveSnoozedTripsFilter();
        var snoozedTrips = Persistence.monitoredTrips.getResponseList(filter, 0, 100);
        return getTripsToUnsnooze(snoozedTrips.data);
    }

    /**
     * Whether a trip should be unsnoozed and monitoring should resume.
     * @return true if the current time is on the calendar day (on or after midnight) after the last checked time.
     */
    public static boolean shouldUnsnooze(long millis) {
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
        getTripsToUnsnooze().forEach(t -> {
            t.snoozed = false;
            Persistence.monitoredTrips.replace(t.id, t);
        });
    }

    @Override
    public void scheduleRecurringJob() {
        // Schedule this job to run once per day.
        LOG.info("UnsnoozeTripsJob every day.");
        Scheduler.scheduleJob(
            new UnsnoozeTripsJob(),
            ConnectedDataManager.getInitialDelayMillis(),
            1,
            TimeUnit.DAYS
        );
    }
}
