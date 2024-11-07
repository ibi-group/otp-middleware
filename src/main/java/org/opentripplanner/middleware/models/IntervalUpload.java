package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A interval upload represents an historic interval (e.g. hour, day) when data was or is planned to be uploaded.
 * If the status is 'pending', the data is waiting to be uploaded. If the status is 'complete' the data has been
 * uploaded.
 */
public class IntervalUpload extends Model {
    // TODO: rename??
    public LocalDateTime uploadHour;

    // TODO: Can this be just the enum type?
    public String status = TripHistoryUploadStatus.PENDING.getValue();

    public IntervalUpload() {
        // Empty constructor for deserialization.
    }

    public IntervalUpload(LocalDateTime uploadHour) {
        this.uploadHour = uploadHour;
        this.status = TripHistoryUploadStatus.PENDING.getValue();
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
}
