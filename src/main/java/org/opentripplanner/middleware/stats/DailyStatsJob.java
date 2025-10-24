package org.opentripplanner.middleware.stats;

import com.google.common.collect.Iterators;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.recurringjobs.RecurringJobScheduler;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;

/**
 * Responsible for collating daily stats and saving them in Mongo.
 */
public class DailyStatsJob implements RecurringJobScheduler, Runnable {

    private static final int ONE_DAY_IN_MINUTES = 60 * 24; // 1 day

    // This property needs to be a valid DateTimeFormatter.ISO_LOCAL_TIME specification.
    // If it's not, this hardcoded default will be used.
    private static final String CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME_DEFAULT =
        "03:00";
    private static final String CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME",
                                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME_DEFAULT);

    private static final Logger LOG = LoggerFactory.getLogger(DailyStatsJob.class);

    public static final String BATCH_ID_FIELD = "batchId";
    private static final String DATE_CREATED_FIELD = "dateCreated";

    public DailyStatsJob() {
        // No actions performed.
    }

    @Override
    public void scheduleRecurringJob() {
        LOG.info("Scheduling daily stats job");

        // Run the collection immediately for the previous day
        // TODO
        // Then compute a delay so that the job runs at the default time.
        long initialDelayMillis = 0L;

        try {
            initialDelayMillis = Scheduler.getInitialDelayMillis(
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME);
        } catch (DateTimeParseException e) {
            LOG.error("CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME value \"{}\" is invalid, using default value \"{}\" instead",
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME,
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME_DEFAULT);
            initialDelayMillis = Scheduler.getInitialDelayMillis(
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_START_TIME_DEFAULT);
        }

        Scheduler.scheduleJob(
            new DailyStatsJob(),
            initialDelayMillis,
            ONE_DAY_IN_MINUTES * 60000L, // milliseconds
            TimeUnit.MILLISECONDS);
    }

    /** Get the stats for OtpUsers */
    private long countOtpUsers() {
        return Persistence.otpUsers.getCount();
    }

    /**
     * Retrieve statistics for a given date.
     */
    public DailyStats retrieveStats(LocalDate date) {
        Bson dateFilter = Filters.and(
            Filters.gte(DATE_CREATED_FIELD, convertToDate(LocalDateTime.of(date, LocalTime.MIDNIGHT))),
            Filters.lt(DATE_CREATED_FIELD, convertToDate(LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT)))
        );
        DistinctIterable<String> uniqueBatchIds = ConnectedDataManager.getUniqueBatchIds(dateFilter);

        // Get user ids for the matching trips
        DistinctIterable<String> uniqueUserIds = Persistence.tripRequests.getDistinctFieldValues(
            "userId",
            Filters.in(BATCH_ID_FIELD, uniqueBatchIds),
            String.class
        );

        DailyStats stats = new DailyStats();
        stats.otpUsers = countOtpUsers();
        stats.tripRequests = Iterators.size(uniqueBatchIds.iterator());
        stats.otpUsersWithTripRequests = Iterators.size(uniqueUserIds.iterator());
        return stats;
    }

    public void run() {
        DailyStats stats = retrieveStats(LocalDate.now());
        Persistence.dailyStats.create(stats);
    }
}
