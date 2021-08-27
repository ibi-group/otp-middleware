package org.opentripplanner.middleware.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.cdp.TripHistoryUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

import java.util.Date;

public class TripHistoryUpload extends Model {
    public Date uploadDate;
    public TripHistoryUploadStatus status;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripHistoryUpload() {
    }

    public TripHistoryUpload(Date uploadDate) {
        this.uploadDate = uploadDate;
        this.status = TripHistoryUploadStatus.PENDING;
    }

    /**
     * Get all incomplete uploads.
     */
    public static FindIterable<TripHistoryUpload> getIncompleteUploads() {
        return Persistence.tripHistoryUploads.getFiltered(
            Filters.ne("status", TripHistoryUploadStatus.COMPLETED.getValue())
        );
    }

    /**
     * Get the last created upload.
     */
    public static TripHistoryUpload getLatest() {
        return getOneOrdered(Sorts.descending("dateCreated"));
    }

    /**
     * Get the first created uploaded.
     */
    public static TripHistoryUpload getFirst() {
        return getOneOrdered(Sorts.ascending("dateCreated"));
    }

    /**
     * Get one upload based on the sort order.
     */
    private static TripHistoryUpload getOneOrdered(Bson sortBy) {
        return Persistence.tripHistoryUploads.getOneFiltered(
            Filters.and(
                Filters.eq("status", TripHistoryUploadStatus.COMPLETED.getValue()),
                Filters.eq("status", TripHistoryUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }

}
