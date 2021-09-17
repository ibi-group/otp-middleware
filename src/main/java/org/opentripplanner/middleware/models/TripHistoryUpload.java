package org.opentripplanner.middleware.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

import java.util.Date;

/**
 * A trip history upload represents an historic date where trip history was or is planned to be uploaded to S3. If the
 * status is 'pending' the trip history is waiting to be uploaded. If the status is 'complete' the trip history has been
 * uploaded.
 */
public class TripHistoryUpload extends Model {

    public Date uploadDate;
    public String status = TripHistoryUploadStatus.PENDING.getValue();

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripHistoryUpload() {
    }

    public TripHistoryUpload(Date uploadDate) {
        this.uploadDate = uploadDate;
        this.status = TripHistoryUploadStatus.PENDING.getValue();
    }

    /**
     * Get the last created upload.
     */
    @BsonIgnore
    public static TripHistoryUpload getLatest() {
        return getOneOrdered(Sorts.descending("dateCreated"));
    }

    /**
     * Get the first created uploaded.
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
                Filters.eq("status", TripHistoryUploadStatus.COMPLETED.getValue()),
                Filters.eq("status", TripHistoryUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }
}
