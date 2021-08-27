package org.opentripplanner.middleware.cdp;

/**
 * This job is responsible for keeping the trip history held on s3 up-to-date by defining the days which should be
 * uploaded and triggering the upload process.
 */
public class TripHistoryUploadJob implements Runnable {
    public void run() {
        ConnectedDataManager.stageUploadDays();
        ConnectedDataManager.processTripHistory();
    }
}
