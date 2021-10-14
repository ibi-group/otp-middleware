package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * This job is responsible for keeping the trip history held on s3 up-to-date by defining the days which should be
 * uploaded and triggering the upload process.
 */
public class TripHistoryUploadJob implements Runnable {

    private static final int HISTORIC_UPLOAD_DAYS_BACK_STOP = 20;

    public void run() {
        stageUploadDays();
        processTripHistory(false);
    }

    /**
     * Add to the upload list any dates between now and the last upload date. This will cover a new day once
     * passed midnight and any days missed due to downtime.
     */
    public static void stageUploadDays() {
        LocalDate now = LocalDate.now();
        TripHistoryUpload latest = TripHistoryUpload.getLatest();
        if (latest == null) {
            // No data held, add the previous day as the first day to be uploaded.
            Persistence.tripHistoryUploads.create(
                new TripHistoryUpload(now.minusDays(1).atStartOfDay().toLocalDate())
            );
        } else {
            Set<LocalDate> betweenDays = DateTimeUtils.getDatesBetween(
                latest.uploadDate.plusDays(1),
                now
            );
            LocalDate historicDateBackStop = getHistoricDateBackStop();
            betweenDays.forEach(day -> {
                if (day.isAfter(historicDateBackStop)) {
                    Persistence.tripHistoryUploads.create(new TripHistoryUpload(day.atStartOfDay().toLocalDate()));
                }
            });
        }
    }

    /**
     * This is the absolute historic date which trip history will be uploaded. This assumes that the service will not be
     * offline longer than this period, but if it is, it will prevent potentially a lot of data being uploaded on
     * start-up which could hinder performance.
     */
    private static LocalDate getHistoricDateBackStop() {
        return LocalDate.now().minusDays(HISTORIC_UPLOAD_DAYS_BACK_STOP);
    }

    /**
     * Process incomplete upload dates. This will be uploads which are flagged as 'pending'. If the upload date is
     * compiled and uploaded successfully, it is flagged as 'complete'.
     */
    public static void processTripHistory(boolean isTest) {
        List<TripHistoryUpload> incompleteUploads = ConnectedDataManager.getIncompleteUploads();
        incompleteUploads.forEach(tripHistoryUpload -> {
            if (ConnectedDataManager.compileAndUploadTripHistory(tripHistoryUpload.uploadDate, isTest)) {
                // Update the status to 'completed' if successfully compiled and uploaded.
                tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
                Persistence.tripHistoryUploads.replace(tripHistoryUpload.id, tripHistoryUpload);
            }
        });
    }

}
