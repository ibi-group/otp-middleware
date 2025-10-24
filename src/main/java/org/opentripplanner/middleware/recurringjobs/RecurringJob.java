package org.opentripplanner.middleware.recurringjobs;

import org.opentripplanner.middleware.bugsnag.BugsnagJobs;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.stats.DailyStatsJob;
import org.opentripplanner.middleware.tripmonitor.jobs.MonitorAllTripsJob;
import org.opentripplanner.middleware.triptracker.TripSurveySenderJob;
import org.opentripplanner.middleware.triptracker.TripSurveyUploadJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum RecurringJob {

    MONITOR_ALL_TRIPS_JOB("monitor-all-trips-job", MonitorAllTripsJob::new),
    TRIP_HISTORY_UPLOAD_JOB("trip-history-upload-job", ConnectedDataManager::new),
    BUGSNAG_EVENT_HANDLING_JOB("bugsnag-event-handing-job", BugsnagJobs::new),
    TRIP_SURVEY_SENDER_JOB("trip-survey-sender-job", TripSurveySenderJob::new),
    TRIP_SURVEY_UPLOAD_JOB("trip-survey-upload-job", TripSurveyUploadJob::new),
    DAILY_STATS_JOB("daily-stats",DailyStatsJob::new);

    private final String commandLineName;
    private final Supplier<RecurringJobScheduler> recurringJobScheduler;

    private static final Logger LOG = LoggerFactory.getLogger(RecurringJob.class);

    RecurringJob(String commandLineName, Supplier<RecurringJobScheduler> recurringJobScheduler) {
        this.commandLineName = commandLineName;
        this.recurringJobScheduler = recurringJobScheduler;
    }

    public static RecurringJob getJobFromCommandLineArgument(String commandLineArgument) {
        return Arrays.stream(RecurringJob.values())
            .filter(job -> job.commandLineName.equalsIgnoreCase(commandLineArgument))
            .findFirst()
            .orElse(null);
    }

    public static Set<RecurringJob> getAllRecurringJobs() {
        return Set.of(RecurringJob.values());
    }

    public static List<String> getAllCommandLineNames() {
        return Stream
            .of(RecurringJob.values())
            .map(r -> "'" + r.commandLineName + "'")
            .collect(Collectors.toList());
    }

    public void schedule() {
        LOG.info("Scheduling job: {}", commandLineName);
        try {
            recurringJobScheduler.get().scheduleRecurringJob();
        } catch (Exception e) {
            LOG.warn("Failed to schedule recurring job: {}", commandLineName, e);
        }

    }
}
