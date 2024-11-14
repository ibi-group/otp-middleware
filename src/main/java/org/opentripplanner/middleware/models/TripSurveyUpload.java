package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadStatus;

import java.time.LocalDateTime;

/**
 * A survey upload represents an historic day when surveys are uploaded to S3. If the
 * status is 'pending' surveys are waiting to be uploaded. If the status is 'complete' the surveys have been
 * uploaded.
 */
public class TripSurveyUpload extends IntervalUpload {

    public TripSurveyUpload() {
        // No-arg constructor for deserialization.
    }

    public TripSurveyUpload(LocalDateTime uploadHour) {
        super(uploadHour);
    }

    public TripSurveyUpload(String id, LocalDateTime uploadHour, IntervalUploadStatus status) {
        super(uploadHour);
        this.id = id;
        this.uploadHour = uploadHour;
        this.status = status;
    }
}
