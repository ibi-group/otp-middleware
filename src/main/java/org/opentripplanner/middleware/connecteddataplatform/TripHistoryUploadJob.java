package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.IntervalUpload;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * This job is responsible for keeping the trip history held on s3 up-to-date by defining the hours which should be
 * uploaded and triggering the upload process.
 */
public class TripHistoryUploadJob extends IntervalUploadJob {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryUploadJob.class);

    public TripHistoryUploadJob() {
        super(LOG, ConnectedDataManager.isReportingDaily());
    }

    @Override
    protected void runInnerLogic() {
        processTripHistory(ConnectedDataManager.CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL, null);
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripHistoryUploads.create(new TripHistoryUpload(time));
    }

    @Override
    protected IntervalUpload getLastUploadCreated() {
        return null;
    }

    /**
     * Process incomplete upload dates. This will be uploads which are flagged as 'pending'. If the upload date is
     * compiled and uploaded successfully, it is flagged as 'complete'.
     */
    public static void processTripHistory(ReportingInterval reportingInterval, Map<String, String> reportedEntities) {
        List<TripHistoryUpload> incompleteUploads = ConnectedDataManager.getIncompleteUploads();
        incompleteUploads.forEach(tripHistoryUpload -> {
            int numRecordsToUpload = ConnectedDataManager.compileAndUploadTripHistory(
                tripHistoryUpload.uploadHour,
                reportingInterval,
                reportedEntities
            );
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
