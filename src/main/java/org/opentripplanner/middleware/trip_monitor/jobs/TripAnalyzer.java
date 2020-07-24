package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
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
                // TODO: This piece might need to go into its own isolated method.

                // Check that a trip should be checked (based on time, day of week, etc.)
                if(shouldCheckMonitoredTrip(trip)) {
                    // Make a request to OTP with the monitored trip params.
                    OtpDispatcherResponse otpDispatcherResponse = OtpDispatcher.sendOtpPlanRequest(trip.queryParams);
                    if (otpDispatcherResponse.statusCode >= 400) {
                        // TODO: report bugsnag
                        LOG.error("Could not reach OTP server. status={}", otpDispatcherResponse.statusCode);
                        return;
                    }
                    // TODO: Should null tripRequestId be fixed?
//                    TripSummary tripSummary = new TripSummary(otpDispatcherResponse.response.plan, otpDispatcherResponse.response.error, null);
                    // TODO: Find the specific itinerary to compare against. For now, just choose the first itin.
                    Itinerary itinerary = otpDispatcherResponse.response.plan.itineraries.get(0);
                    // BEGIN CHECKS
                    // Check for new alerts.
                    checkTripForNewAlerts(trip, itinerary);
                } else {
                    LOG.debug("Skipping check for trip: {}", trip.id);
                }

                // TODO check for notifications that should be sent related to service alerts

                // TODO check for notifications that should be sent related to delays

                // TODO check for notifications that should be sent related to itinerary changes

                // TODO send relevant notifications

                LOG.info("Finished analyzing trip {}", trip);

                // remove lock on trip
                monitoredTripLocks.remove(trip);

                analyzerIsIdle.set(true);
            }
        } catch (InterruptedException e) {
            LOG.error("error encountered while waiting during TripAnalyzer.", e);
        }
    }

    private void checkTripForNewAlerts(MonitoredTrip trip, Itinerary itinerary) {
        if (!trip.notifyOnAlert) {
            LOG.debug("Notify on alert is disabled for trip {}. Skipping check.", trip.id);
            return;
        }
        // If journey state is already tracking alerts from previous checks, see if they have changed.
        if (trip.journeyState.alertIds.size() > 0) {
            // If the alerts have changed, notify user.
            // TODO
        } else {
            // Check if there are any new alerts present in the response.
//            itinerary.legs.stream().forEach(leg -> leg.);
//            if ()
        }
    }

    private boolean shouldCheckMonitoredTrip(MonitoredTrip trip) {
        LocalDate now = LocalDate.now();
        LocalDate tripTime;
        try {
            tripTime = DateUtils.getDateFromString(trip.tripTime, "HH:mm");
        } catch (DateTimeParseException e) {
            // TODO: Bugsnag report
            LOG.error("Could not parse trip time", e);
            return false;
        }
        // Trip is active today.
        return trip.isActiveOnDate(now) &&
            // Trip has not already occurred.
            tripTime.isBefore(now);
        // TODO: Check that last comparison was X minutes ago?
        // TODO: Check that journey state is not flagged?
    }
}
