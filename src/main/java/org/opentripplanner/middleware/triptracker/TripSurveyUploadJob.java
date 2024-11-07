package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadJob;
import org.opentripplanner.middleware.models.IntervalUpload;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    public TripSurveyUploadJob() {
        super(LOG, true);
    }

    @Override
    protected void runInnerLogic() {

    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripSurveyUploads.create(new TripSurveyUpload(time));
    }

    @Override
    protected IntervalUpload getLastUploadCreated() {
        return TripSurveyUpload.getLastCreated();
    }
}
