package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;

import java.util.UUID;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

public class ManageTripTracking {

    private ManageTripTracking() {
    }

    public static final int TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS
        = getConfigPropertyAsInt("TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS", 5);

    /**
     * Start tracking by providing a unique journey id and tracking update frequency to the caller.
     */
    public static StartTrackingResponse startTrackingTrip(TrackingPayload payload) {

        // Create unique journey id for this trip. If the same trip is repeated it will not clash with previous
        // journeys of the same trip.
        var journeyId = UUID.randomUUID().toString();

        // Start tracking journey.
        var trackedJourney = new TrackedJourney(journeyId, payload);
        Persistence.trackedJourneys.create(trackedJourney);

        // Provide response.
        return new StartTrackingResponse(
            TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
            getInstructions(TripStage.START),
            journeyId,
            getTripStatus(TripStage.START)
        );
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static UpdateTrackingResponse updateTracking(TrackingPayload payLoad, TrackedJourney trackedJourney) {

        // Update tracked journey.
        trackedJourney.update(payLoad);
        Persistence.trackedJourneys.replace(trackedJourney.id, trackedJourney);

        // Provide response.
        return new UpdateTrackingResponse(
            getInstructions(TripStage.UPDATE),
            getTripStatus(TripStage.UPDATE)
        );
    }

    /**
     * End tracking by saving the end condition and date.
     */
    public static void endTracking(TrackedJourney trackedJourney) {
        trackedJourney.end("Tracking terminated by user.");
        Persistence.trackedJourneys.replace(trackedJourney.id, trackedJourney);
    }

    /**
     * Provides the instructions for the user based on the trip stage and location.
     */
    private static String getInstructions(TripStage tripStage) {
        // This is to be expanded on in later PRs. For now, it is used for unit testing.
        switch (tripStage) {
            case START:
                return TripInstruction.GET_ON_BUS.name();
            case UPDATE:
                return TripInstruction.STAY_ON_BUS.name();
            default:
                return TripInstruction.NO_INSTRUCTION.name();
        }
    }

    /**
     * Provides the trip status based on the trip stage and location.
     */
    private static String getTripStatus(TripStage tripStage) {
        // This is to be expanded on in later PRs. For now, it is used for unit testing.
        switch (tripStage) {
            case START:
            case UPDATE:
                return TripStatus.ON_TRACK.name();
            default:
                return TripStatus.NO_STATUS.name();
        }
    }
}
