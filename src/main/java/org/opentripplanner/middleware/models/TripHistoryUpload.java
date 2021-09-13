package org.opentripplanner.middleware.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.cdp.TripHistoryUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

import java.util.Date;

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
     * Get all incomplete uploads.
     */
    @BsonIgnore
    public static FindIterable<TripHistoryUpload> getIncompleteUploads() {
        return Persistence.tripHistoryUploads.getFiltered(
            Filters.ne("status", TripHistoryUploadStatus.COMPLETED.getValue())
        );
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
