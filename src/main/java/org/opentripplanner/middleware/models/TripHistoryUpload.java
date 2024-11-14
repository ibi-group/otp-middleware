package org.opentripplanner.middleware.models;

import org.bson.codecs.pojo.annotations.BsonIgnore;
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
        return IntervalUpload.getFirst(Persistence.tripHistoryUploads);
    }
}
