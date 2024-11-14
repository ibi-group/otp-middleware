package org.opentripplanner.middleware.models;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadStatus;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A interval upload represents an historic interval (e.g. hour, day) when data was or is planned to be uploaded.
 * If the status is 'pending', the data is waiting to be uploaded. If the status is 'complete' the data has been
 * uploaded.
 */
public class IntervalUpload extends Model {
    public static final String STATUS_FIELD_NAME = "status";

    public LocalDateTime uploadHour; // Regardless of whether dealing with hours or days.

    public IntervalUploadStatus status = IntervalUploadStatus.PENDING;

    public IntervalUpload() {
        // Empty constructor for deserialization.
    }

    public IntervalUpload(LocalDateTime uploadHour) {
        this.uploadHour = uploadHour;
        this.status = IntervalUploadStatus.PENDING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        IntervalUpload that = (IntervalUpload) o;
        return Objects.equals(uploadHour, that.uploadHour) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), uploadHour, status);
    }

    /**
     * Get the last created upload regardless of status.
     */
    @BsonIgnore
    public static <U extends IntervalUpload> U getLastUploadCreated(TypedPersistence<U> persistence) {
        return getOneOrdered(persistence, Sorts.descending("dateCreated"));
    }

    /**
     * Get the first created upload regardless of status.
     */
    @BsonIgnore
    public static <U extends IntervalUpload> U getFirstUpload(TypedPersistence<U> persistence) {
        return getOneOrdered(persistence, Sorts.ascending("dateCreated"));
    }

    /**
     * Get one upload based on the sort order.
     */
    public static <U extends IntervalUpload> U getOneOrdered(TypedPersistence<U> persistence, Bson sortBy) {
        return persistence.getOneFiltered(
            Filters.or(
                Filters.eq(STATUS_FIELD_NAME, IntervalUploadStatus.COMPLETED.getValue()),
                Filters.eq(STATUS_FIELD_NAME, IntervalUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }

    /**
     * Get all incomplete uploads.
     */
    public static <U extends IntervalUpload> List<U> getIncompleteUploads(TypedPersistence<U> persistence) {
        FindIterable<U> incompleteUploads = persistence.getFiltered(
            Filters.ne(IntervalUpload.STATUS_FIELD_NAME, IntervalUploadStatus.COMPLETED.getValue())
        );
        return incompleteUploads.into(new ArrayList<>());
    }
}
