package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
                if (MonitoredTripLocks.isLocked(trip)) {
                    LOG.warn("Skipping trip analysis due to existing lock on trip: {}", trip);
                    analyzerIsIdle.set(true);
                    continue;
                }

                // Refetch the trip from the database. This is to ensure the trip has any updates made to the trip
                // between when the trip was placed in the analysis queue and the current time.
                String tripId = trip.id;
                trip = Persistence.monitoredTrips.getById(tripId);
                if (trip == null) {
                    // trip was deleted between the time when it was placed in the queue and the current time. Don't
                    // analyze the trip.
                    LOG.info("Trip {} was deleted before analysis began.", tripId);
                    analyzerIsIdle.set(true);
                    continue;
                }

                LOG.info("Analyzing trip {}", tripId);

                // place lock on trip
                MonitoredTripLocks.lock(trip);

                /////// BEGIN TRIP ANALYSIS
                try {
                    new CheckMonitoredTrip(trip).run();
                } catch (Exception e) {
                    LOG.error("Error encountered while checking monitored trip", e);
                    // FIXME bugsnag
                }
                LOG.info("Finished analyzing trip {}", tripId);

                // remove lock on trip
                MonitoredTripLocks.unlock(trip);

                analyzerIsIdle.set(true);
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during TripAnalyzer.", e);
        }
    }
}
