package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This job will analyze all monitored trips and create further individual tasks to analyze each individual trip.
 */
public class MonitorAllTripsJob implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorAllTripsJob.class);

    private final int numCores = Runtime.getRuntime().availableProcessors();
    private final int BLOCKING_QUEUE_SIZE = numCores;
    private final int BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS = 250;
    private final int BLOCKING_QUEUE_INSERT_TIMEOUT_SECONDS = 30;
    private final int N_TRIP_ANALYZERS = numCores;

    @Override
    public void run() {
        LOG.info("MonitorAllTripsJob started");
        // analyze all trips

        // create a blocking queue of monitored trips to process
        BlockingQueue<MonitoredTrip> tripAnalysisQueue = new ArrayBlockingQueue<>(BLOCKING_QUEUE_SIZE);

        // create an Atomic Boolean for TripAnalyzer threads to check whether the queue is actually depleted
        AtomicBoolean queueDepleted = new AtomicBoolean();

        // create a list of analyzer statuses to check whether any analyzers are still processing trips
        List<AtomicBoolean> analyzerStatuses = new ArrayList<>();

        // create new threads for analyzers of monitored trips
        for (int j = 0; j < N_TRIP_ANALYZERS; j++) {
            AtomicBoolean analyzerIsIdle = new AtomicBoolean();
            analyzerStatuses.add(analyzerIsIdle);
            new Thread(new TripAnalyzer(tripAnalysisQueue, queueDepleted, analyzerIsIdle)).start();
        }

        try {
            // request all monitored trips from the mongo collection
            for (MonitoredTrip monitoredTrip : Persistence.monitoredTrips.getAll()) {
                // attempt to add trip to tripAnalysisQueue until a spot opens up in the queue. If the timeout is
                // exceeded, an InterruptedException is throw.
                tripAnalysisQueue.offer(monitoredTrip, BLOCKING_QUEUE_INSERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // wait for queue to deplete
            while (tripAnalysisQueue.size() > 0) {
                Thread.sleep(BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS);
            }
            queueDepleted.set(true);

            // wait for analyzers to complete
            while (!allAnalyzersAreIdle(analyzerStatuses)) {
                Thread.sleep(BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS);
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during MonitorAllTripsJob.");
            e.printStackTrace();
            return;
        }

        // analysis of all trips finished
        LOG.info("Analysis of all MonitoredTrips completed");

        // TODO report successful run to error & notification system

        LOG.info("MonitorAllTripsJob completed");
    }

    /**
     * Checks each analyzer idle status and returns false if any are not idle.
     */
    private boolean allAnalyzersAreIdle(List<AtomicBoolean> analyzerStatuses) {
        for (AtomicBoolean analyzerStatus : analyzerStatuses) {
            if (!analyzerStatus.get()) {
                return false;
            }
        }
        return true;
    }
}
