package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TripAnalyzer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TripAnalyzer.class);

    private final int BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS = 250;

    private final BlockingQueue<MonitoredTrip> tripAnalysisQueue;
    private final AtomicBoolean queueDepleted;

    public TripAnalyzer(BlockingQueue<MonitoredTrip> tripAnalysisQueue, AtomicBoolean queueDepleted) {
        this.tripAnalysisQueue = tripAnalysisQueue;
        this.queueDepleted = queueDepleted;
    }

    @Override
    public void run() {
        try {
            while (!queueDepleted.get()) {
                // get the next monitored trip from the queue
                MonitoredTrip trip;
                try {
                    trip = tripAnalysisQueue.poll(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    LOG.warn("TripAnalyzer thread interrupted");
                    e.printStackTrace();
                    Thread.sleep(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS);
                    continue;
                }

                // The implementation of the ArrayBlockingQueue can result in null items being returned if the wait is
                // exceeded on an empty queue. Therefore, check if the trip is null and if so, wait and then continue.
                if (trip == null) {
                    Thread.sleep(BLOCKING_QUEUE_POLL_TIMEOUT_MILLIS);
                    continue;
                }

                // TODO verify that a lock hasn't been placed on trip by another trip analyzer task
                // TODO place lock on trip

                LOG.info("Analyzing trip {}", trip);

                // TODO check if trip should be analyzed

                // TODO check for notifications that should be sent related to service alerts

                // TODO check for notifications that should be sent related to delays

                // TODO check for notifications that should be sent related to itinerary changes

                // TODO send relevant notifications

                LOG.info("Finished analyzing trip {}", trip);

                // TODO remove lock on trip
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during TripAnalyzer.");
            e.printStackTrace();
        }
    }
}
