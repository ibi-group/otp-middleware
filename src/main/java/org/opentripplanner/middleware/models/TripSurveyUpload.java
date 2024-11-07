package org.opentripplanner.middleware.models;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

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

    public TripSurveyUpload(String id, LocalDateTime uploadHour, TripHistoryUploadStatus status) {
        super(uploadHour);
        this.id = id;
        this.uploadHour = uploadHour;
        this.status = status.toString();
    }

    /**
     * Get the last created survey upload regardless of status.
     */
    @BsonIgnore
    public static TripSurveyUpload getLastCreated() {
        return getOneOrdered(Sorts.descending("dateCreated"));
    }

    /**
     * Get the first created survey upload regardless of status.
     */
    @BsonIgnore
    public static TripSurveyUpload getFirst() {
        return getOneOrdered(Sorts.ascending("dateCreated"));
    }

    /**
     * Get one upload based on the sort order.
     */
    private static TripSurveyUpload getOneOrdered(Bson sortBy) {
        return Persistence.tripSurveyUploads.getOneFiltered(
            Filters.or(
                Filters.eq("status", TripHistoryUploadStatus.COMPLETED.getValue()),
                Filters.eq("status", TripHistoryUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }
}
