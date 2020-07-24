package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.trip_monitor.CheckMonitoredTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.opentripplanner.middleware.trip_monitor.jobs.MonitorAllTripsJob.monitoredTripLocks;

public class TripAnalyzer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TripAnalyzer.class);

    private final int BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS = 250;

    private final AtomicBoolean analyzerIsIdle;
    private final BlockingQueue<MonitoredTrip> tripAnalysisQueue;
    private final AtomicBoolean queueDepleted;

    public TripAnalyzer(
        BlockingQueue<MonitoredTrip> tripAnalysisQueue,
        AtomicBoolean queueDepleted,
        AtomicBoolean analyzerIsIdle
    ) {
        this.tripAnalysisQueue = tripAnalysisQueue;
        this.queueDepleted = queueDepleted;
        this.analyzerIsIdle = analyzerIsIdle;
    }

    @Override
    public void run() {
        try {
            while (!queueDepleted.get()) {
                analyzerIsIdle.set(false);

                // get the next monitored trip from the queue
                MonitoredTrip trip;
                try {
                    trip = tripAnalysisQueue.poll(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    LOG.warn("TripAnalyzer thread interrupted");
                    e.printStackTrace();
                    analyzerIsIdle.set(true);
                    Thread.sleep(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS);
                    continue;
                }

                // The implementation of the ArrayBlockingQueue can result in null items being returned if the wait is
                // exceeded on an empty queue. Therefore, check if the trip is null and if so, wait and then continue.
                if (trip == null) {
                    Thread.sleep(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS);
                    analyzerIsIdle.set(true);
                    continue;
                }

                // verify that a lock hasn't been placed on trip by another trip analyzer task
                if (monitoredTripLocks.containsKey(trip)) {
                    LOG.warn("Skipping trip analysis due to existing lock on trip: {}", trip);
                    analyzerIsIdle.set(true);
                    continue;
                }

                LOG.info("Analyzing trip {}", trip);

                // place lock on trip
                monitoredTripLocks.put(trip, true);

                /////// BEGIN TRIP ANALYSIS
                new CheckMonitoredTrip(trip).run();

                LOG.info("Finished analyzing trip {}", trip);

                // remove lock on trip
                monitoredTripLocks.remove(trip);

                analyzerIsIdle.set(true);
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during TripAnalyzer.", e);
        }
    }
}
