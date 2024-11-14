package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL;

/**
 * This job is responsible for keeping the trip history held on s3 up-to-date by defining the hours which should be
 * uploaded and triggering the upload process.
 */
public class TripHistoryUploadJob extends IntervalUploadJob<TripHistoryUpload> {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryUploadJob.class);

    private final Map<String, String> reportedEntities;

    public TripHistoryUploadJob() {
        super(LOG, CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL, Persistence.tripHistoryUploads);
        this.reportedEntities = null;
    }

    public TripHistoryUploadJob(ReportingInterval reportingInterval, Map<String, String> reportedEntities) {
        super(LOG, reportingInterval, Persistence.tripHistoryUploads);
        this.reportedEntities = reportedEntities;
    }

    @Override
    protected void processInterval(TripHistoryUpload upload) {
        int numRecordsToUpload = ConnectedDataManager.compileAndUploadTripHistory(
            upload.uploadHour,
            reportingInterval,
            reportedEntities
        );
        if (numRecordsToUpload != Integer.MIN_VALUE) {
            // If successfully compiled and updated, update the status to 'completed' and record the number of trip
            // requests uploaded (if any).
            upload.status = IntervalUploadStatus.COMPLETED;
            upload.numTripRequestsUploaded = numRecordsToUpload;
            Persistence.tripHistoryUploads.replace(upload.id, upload);
        }
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripHistoryUploads.create(new TripHistoryUpload(time));
    }
}
