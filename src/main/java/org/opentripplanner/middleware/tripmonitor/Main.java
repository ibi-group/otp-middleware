package org.opentripplanner.middleware.tripmonitor;

import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.jobs.MonitorAllTripsJob;
import org.opentripplanner.middleware.utils.Scheduler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.ConfigUtils.loadConfig;

public class Main {
    public static void main(String[] args) throws IOException {
        // Load configuration.
        loadConfig(args);

        // Connect to MongoDB.
        Persistence.initialize();

        // Schedule recurring Monitor All Trips Job
        MonitorAllTripsJob monitorAllTripsJob = new MonitorAllTripsJob();
        Scheduler.scheduleJob(
            monitorAllTripsJob,
            0,
            1,
            TimeUnit.MINUTES
        );
    }
}
