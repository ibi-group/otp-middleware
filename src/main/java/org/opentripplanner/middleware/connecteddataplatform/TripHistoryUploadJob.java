package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * This job is responsible for keeping the trip history held on s3 up-to-date by defining the hours which should be
 * uploaded and triggering the upload process.
 */
public class TripHistoryUploadJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryUploadJob.class);
    private static final int HISTORIC_UPLOAD_HOURS_BACK_STOP = 24;

    public void run() {
        if (ConnectedDataManager.isAggregationDaily()) {
            stageUploadDays();
        } else {
            stageUploadHours();
        }
        processTripHistory(false);
    }

    /**
     * Add to the trip history upload list any hours between the previous whole hour and the last created (pending or
     * completed) trip history upload. This will cover any hours missed due to downtime and add the latest upload hour
     * if not already accounted for.
     */
    public static void stageUploadHours() {
        stageUploadTimes(DateTimeUtils.getPreviousWholeHourFrom(LocalDateTime.now()), ChronoUnit.HOURS);
    }

    /**
     * Add to the trip history upload list any days between the previous day and the last created (pending or
     * completed) trip history upload. This will cover any days missed due to downtime and add the latest upload day
     * if not already accounted for.
     */
    public static void stageUploadDays() {
        stageUploadTimes(DateTimeUtils.getPreviousDayFrom(LocalDateTime.now()), ChronoUnit.DAYS);
    }

    /**
     * Add to the trip history upload list any hours/days between the previous whole hour/day and the last created
     * (pending or completed) trip history upload. This will cover any hours/days missed due to downtime,
     * up to HISTORIC_UPLOAD_HOURS_BACK_STOP hours, and add the latest upload hour/day if not already accounted for.
     */
    private static void stageUploadTimes(LocalDateTime previousTime, ChronoUnit chronoUnit) {
        TripHistoryUpload lastCreated = TripHistoryUpload.getLastCreated();
        if (lastCreated == null) {
            // Stage first ever upload hour.
            Persistence.tripHistoryUploads.create(new TripHistoryUpload(previousTime));
            LOG.debug("Staging first ever upload hour: {}.", previousTime);
            return;
        }
        // Stage all time between the last time uploaded and an hour/day ago.
        List<LocalDateTime> intermediateTimes = DateTimeUtils.getTimeUnitsBetween(
            lastCreated.uploadHour,
            previousTime,
            chronoUnit
        );
        intermediateTimes.forEach(uploadHour -> {
            if (uploadHour.isAfter(getHistoricDateTimeBackStop())) {
                LOG.debug(
                    "Staging hour: {} that is between last created: {} and the previous whole hour: {}",
                    lastCreated,
                    previousTime,
                    uploadHour
                );
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(uploadHour));
            }
        });
        if (!lastCreated.uploadHour.isEqual(previousTime)) {
            // Last created is not the latest upload hour, so stage an hour ago.
            Persistence.tripHistoryUploads.create(new TripHistoryUpload(previousTime));
            LOG.debug("Last created {} is older than the latest {}, so staging.", lastCreated, previousTime);
        }
    }

    /**
     * This is the absolute historic date/time which trip history will be uploaded. This assumes that the service will
     * not be offline longer than this period, but if it is, it will prevent potentially a lot of data being uploaded on
     * start-up which will impact performance.
     */
    private static LocalDateTime getHistoricDateTimeBackStop() {
        return LocalDateTime.now().minusHours(HISTORIC_UPLOAD_HOURS_BACK_STOP);
    }

    /**
     * Process incomplete upload dates. This will be uploads which are flagged as 'pending'. If the upload date is
     * compiled and uploaded successfully, it is flagged as 'complete'.
     */
    public static void processTripHistory(boolean isTest) {
        List<TripHistoryUpload> incompleteUploads = ConnectedDataManager.getIncompleteUploads();
        incompleteUploads.forEach(tripHistoryUpload -> {
            int numRecordsToUpload = ConnectedDataManager.compileAndUploadTripHistory(tripHistoryUpload.uploadHour, isTest);
            if (numRecordsToUpload != Integer.MIN_VALUE) {
                // If successfully compiled and updated, update the status to 'completed' and record the number of trip
                // requests uploaded (if any).
                tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
                tripHistoryUpload.numTripRequestsUploaded = numRecordsToUpload;
                Persistence.tripHistoryUploads.replace(tripHistoryUpload.id, tripHistoryUpload);
            }
        });
    }

}
