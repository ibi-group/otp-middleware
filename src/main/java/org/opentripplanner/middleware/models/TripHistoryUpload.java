package org.opentripplanner.middleware.models;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

import java.time.LocalDateTime;

/**
 * A trip history upload represents an historic hour when trip history was or is planned to be uploaded to S3. If the
 * status is 'pending' the trip history is waiting to be uploaded. If the status is 'complete' the trip history has been
 * uploaded.
 */
public class TripHistoryUpload extends IntervalUpload {

    public int numTripRequestsUploaded = 0;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripHistoryUpload() {
    }

    public TripHistoryUpload(LocalDateTime uploadHour) {
        super(uploadHour);
    }

    /**
     * Get the first created trip history upload regardless of status.
     */
    @BsonIgnore
    public static TripHistoryUpload getFirst() {
        return getOneOrdered(Sorts.ascending("dateCreated"));
    }

    /**
     * Get one upload based on the sort order.
     */
    private static TripHistoryUpload getOneOrdered(Bson sortBy) {
        return Persistence.tripHistoryUploads.getOneFiltered(
            Filters.or(
                Filters.eq("status", IntervalUploadStatus.COMPLETED.getValue()),
                Filters.eq("status", IntervalUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }
}
