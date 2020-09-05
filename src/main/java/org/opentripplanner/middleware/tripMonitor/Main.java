package org.opentripplanner.middleware.tripMonitor;

import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripMonitor.jobs.MonitorAllTripsJob;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.Scheduler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException {
        // Load configuration.
        ConfigUtils.loadConfig(args);

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
