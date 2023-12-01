package org.opentripplanner.middleware.tripmonitor.jobs;

import com.mongodb.BasicDBObject;
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
 * This job will analyze applicable monitored trips and create further individual tasks to analyze each individual trip.
 */
public class MonitorAllTripsJob implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorAllTripsJob.class);
    public static final int ONE_MINUTE_IN_MILLIS = 60000;

    private final int numCores = Runtime.getRuntime().availableProcessors();
    private final int BLOCKING_QUEUE_SIZE = numCores;
    private static final int BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS = 250;
    private static final int BLOCKING_QUEUE_INSERT_TIMEOUT_SECONDS = 30;
    private final int N_TRIP_ANALYZERS = numCores;

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        LOG.info("MonitorAllTripsJob started");
        // analyze all trips

        // create a blocking queue of monitored trip IDs to process
        BlockingQueue<String> tripAnalysisQueue = new ArrayBlockingQueue<>(BLOCKING_QUEUE_SIZE);

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
            // Request at once all applicable monitored trip IDs from the Mongo collection, and loop through them.
            // If we looped using a Mongo-provided iterator instead, and the Mongo connection is dropped for any reason
            // while the iterator is open, this thread would become blocked and prevent subsequent runs of this job.
            // Performance note: Don't retrieve the full data for each trip at this time.
            // This saves bandwidth and memory, as only the ID field is used to set up this job.
            // The full data for each trip will be fetched at the time the actual analysis takes place.
            List<String> allTripIds = Persistence.monitoredTrips.getDistinctFieldValues(
                "_id",
                makeTripFilter(),
                String.class
            ).into(new ArrayList<>());
            for (String tripId : allTripIds) {
                // attempt to add trip ID to tripAnalysisQueue until a spot opens up in the queue. If the timeout is
                // exceeded, an InterruptedException is throw.
                tripAnalysisQueue.offer(tripId, BLOCKING_QUEUE_INSERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            // wait for queue to deplete
            int queueIterations = 0;
            while (!tripAnalysisQueue.isEmpty()) {
                Thread.sleep(BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS);
                queueIterations++;
                // Report queue status every minute (unless this job finishes before).
                int runMillis = queueIterations * BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS;
                if ((runMillis % ONE_MINUTE_IN_MILLIS) == 0) {
                    LOG.info("There are {} queued. after {} sec.", tripAnalysisQueue.size(), runMillis / 1000);
                }
            }
            queueDepleted.set(true);

            // wait for analyzers to complete
            int idleIterations = 0;
            while (!allAnalyzersAreIdle(analyzerStatuses)) {
                Thread.sleep(BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS);
                idleIterations++;
                // Report analyzers statuses every minute (unless this job finishes before).
                int runMillis = idleIterations * BLOCKING_QUEUE_DEPLETE_WAIT_TIME_MILLIS;
                if ((runMillis % ONE_MINUTE_IN_MILLIS) == 0) {
                    long notIdleCount = analyzerStatuses.stream().filter(s -> !s.get()).count();
                    LOG.info("There are {} analyzers not idle after {} sec.", notIdleCount, runMillis / 1000);
                }
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during MonitorAllTripsJob.");
            e.printStackTrace();
            return;
        }

        // analysis of all trips finished
        LOG.info("Analysis of all MonitoredTrips completed");

        // TODO report successful run to error & notification system

        LOG.info("MonitorAllTripsJob completed in {} sec", (System.currentTimeMillis() - start) / 1000);
    }

    /**
     * Create a BSON clause to filter out trips that would not be checked.
     */
    private static BasicDBObject makeTripFilter() {
        BasicDBObject tripFilter = new BasicDBObject();

        //.Trips must be active.
        tripFilter.put("isActive", true);

        // Other conditions (e.g. in CheckMonitoredTrip) that would result in a trip to be not checked
        // should eventually be moved here.

        return tripFilter;
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
