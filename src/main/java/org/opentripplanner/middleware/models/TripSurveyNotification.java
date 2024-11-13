package org.opentripplanner.middleware.models;

import java.util.Date;
import java.util.UUID;

/** Contains information regarding survey notifications sent after a trip is completed. */
public class TripSurveyNotification {

    public static final String TIME_SENT_FIELD = "timeSent";

    /**
     * Unique ID to link a survey entry to the corresponding notification
     * (and to find which notifications were dismissed without opening the survey)
     */
    public String id;

    /** Date/time when the trip survey notification was sent. */
    public Date timeSent;

    /** The {@link TrackedJourney} (and, indirectly, the {@link MonitoredTrip}) that this notification refers to. */
    public String journeyId;

    public TripSurveyNotification() {
        // Default constructor for deserialization
    }

    public TripSurveyNotification(String id, Date timeSent, String journeyId) {
        this.id = id;
        this.timeSent = timeSent;
        this.journeyId = journeyId;
    }
}
