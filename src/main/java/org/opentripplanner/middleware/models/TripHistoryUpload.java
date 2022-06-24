package org.opentripplanner.middleware.models;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.persistence.Persistence;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A trip history upload represents an historic hour when trip history was or is planned to be uploaded to S3. If the
 * status is 'pending' the trip history is waiting to be uploaded. If the status is 'complete' the trip history has been
 * uploaded.
 */
public class TripHistoryUpload extends Model {

    public LocalDateTime uploadHour;
    public String status = TripHistoryUploadStatus.PENDING.getValue();
    public int numTripRequestsUploaded = 0;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripHistoryUpload() {
    }

    public TripHistoryUpload(LocalDateTime uploadHour) {
        this.uploadHour = uploadHour;
        this.status = TripHistoryUploadStatus.PENDING.getValue();
    }

    /**
     * Get the last created trip history upload regardless of status.
     */
    @BsonIgnore
    public static TripHistoryUpload getLastCreated() {
        return getOneOrdered(Sorts.descending("dateCreated"));
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
                Filters.eq("status", TripHistoryUploadStatus.COMPLETED.getValue()),
                Filters.eq("status", TripHistoryUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TripHistoryUpload that = (TripHistoryUpload) o;
        return Objects.equals(uploadHour, that.uploadHour) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), uploadHour, status);
    }
}
