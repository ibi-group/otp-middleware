package org.opentripplanner.middleware.tripmonitor.jobs;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import spark.Request;

import java.util.concurrent.ConcurrentHashMap;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * A helper class that manages locks placed on individual monitored trip instances.
 */
public class MonitoredTripLocks {
    /** the maximum amount of time in milliseconds to wait for a lock to be released */
    private static final int MAX_UNLOCKING_WAIT_TIME_MILLIS = 4000;
    /** the amount of time in milliseconds to wait to check if a lock has been released */
    private static final int LOCK_CHECK_WAIT_MILLIS = 500;

    private static final ConcurrentHashMap<String, Boolean> locks = new ConcurrentHashMap<>();

    /**
     * Locks the given MonitoredTrip
     */
    public static void lock(String tripId) {
        locks.put(tripId, true);
    }

    /**
     * Removes a lock for a given MonitoredTrip
     */
    public static void unlock(String tripId) {
        locks.remove(tripId);
    }

    /**
     * Returns true if a lock exists for the given MonitoredTrip
     */
    public static boolean isLocked(String tripId) {
        return locks.containsKey(tripId);
    }

    /**
     * Attempts to lock the trip for updating within the context of a web request. If an existing monitored trip check
     * is currently happening, this method will retry for 4 seconds to obtain a lock for the trip. If a lock couldn't be
     * obtained, then the request is halted.
     */
    public static void lockTripForUpdating(MonitoredTrip monitoredTrip, Request req) {
        // Wait for any existing CheckMonitoredTrip jobs to complete before proceeding
        String busyMessage = "A trip monitor check prevented the trip from being updated. Please try again in a moment.";
        if (isLocked(monitoredTrip.id)) {
            int timeWaitedMillis = 0;
            do {
                try {
                    Thread.sleep(LOCK_CHECK_WAIT_MILLIS);
                } catch (InterruptedException e) {
                    logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, busyMessage);
                }
                timeWaitedMillis += LOCK_CHECK_WAIT_MILLIS;

                // if the lock has been released, exit this wait loop
                if (!isLocked(monitoredTrip.id)) break;
            } while (timeWaitedMillis <= MAX_UNLOCKING_WAIT_TIME_MILLIS);
        }

        // If a lock still exists, prevent the update
        if (isLocked(monitoredTrip.id)) {
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, busyMessage);
            return;
        }

        // lock the trip so that the a CheckMonitoredTrip job won't concurrently analyze/update the trip.
        lock(monitoredTrip.id);
    }
}
