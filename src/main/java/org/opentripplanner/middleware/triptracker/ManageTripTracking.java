package org.opentripplanner.middleware.triptracker;

import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.StartTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.UpdateTrackingResponse;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class ManageTripTracking {

    private ManageTripTracking() {
    }

    public static final int TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS
        = getConfigPropertyAsInt("TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS", 5);

    /**
     * Start tracking by providing a unique journey id and tracking update frequency to the caller.
     */
    public static StartTrackingResponse startTracking(Request request) {
        StartTrackingPayload payload = StartTrackingPayload.getPayloadFromRequest(request);
        if (payload == null || !isTripAssociatedWithUser(request, payload.tripId) || isJourneyOngoing(request, payload.tripId)) {
            return null;
        }

        // Start tracking journey.
        var trackedJourney = new TrackedJourney(payload);
        Persistence.trackedJourneys.create(trackedJourney);

        // Provide response.
        return new StartTrackingResponse(
            TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
            getInstructions(TripStage.START),
            trackedJourney.id,
            getTripStatus(TripStage.START)
        );
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static UpdateTrackingResponse updateTracking(Request request) {
        UpdatedTrackingPayload payload = UpdatedTrackingPayload.getPayloadFromRequest(request);
        if (payload == null) {
            return null;
        }
        TrackedJourney trackedJourney = getActiveJourney(request, payload.journeyId);
        if (trackedJourney == null || !isTripAssociatedWithUser(request, trackedJourney.tripId)) {
            return null;
        }

        // Update tracked journey.
        trackedJourney.update(payload);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.LOCATIONS_FIELD_NAME, trackedJourney.locations);

        // Provide response.
        return new UpdateTrackingResponse(
            getInstructions(TripStage.UPDATE),
            getTripStatus(TripStage.UPDATE)
        );
    }

    /**
     * End tracking by saving the end condition and date.
     */
    public static void endTracking(Request request) {
        EndTrackingPayload payload = EndTrackingPayload.getPayloadFromRequest(request);
        if (payload == null) {
            return;
        }
        TrackedJourney trackedJourney = getActiveJourney(request, payload.journeyId);
        if (trackedJourney == null || !isTripAssociatedWithUser(request, trackedJourney.tripId)) {
            return;
        }
        completeJourney(trackedJourney, false);
    }

    /**
     * Forcibly end tracking based on the trip id. This is to be used only when the journey id is unknown and the end
     * tracking request can not be made. This prevents the scenario of a journey being 'lost' and the user not been able
     * to restart it.
     */
    public static void forciblyEndTracking(Request request) {
        ForceEndTrackingPayload payload = ForceEndTrackingPayload.getPayloadFromRequest(request);
        if (payload == null) {
            return;
        }
        TrackedJourney trackedJourney = getActiveJourneyForTripId(request, payload.tripId);
        if (trackedJourney == null || !isTripAssociatedWithUser(request, trackedJourney.tripId)) {
            return;
        }
        completeJourney(trackedJourney, true);
    }

    /**
     * Complete a journey by defining the ending type, time and condition.
     */
    private static void completeJourney(TrackedJourney trackedJourney, boolean isForciblyEnded) {
        trackedJourney.end(isForciblyEnded);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_TIME_FIELD_NAME, trackedJourney.endTime);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_CONDITION_FIELD_NAME, trackedJourney.endCondition);
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

    /**
     * Confirm that the monitored trip (that the user is on) belongs to them.
     */
    private static boolean isTripAssociatedWithUser(Request request, String tripId) {
        var user = Auth0Connection.getUserFromRequest(request);

        var monitoredTrip = Persistence.monitoredTrips.getById(tripId);
        if (monitoredTrip == null || (user.otpUser != null && !monitoredTrip.userId.equals(user.otpUser.id))) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Monitored trip is not associated with this user!");
            return false;
        }
        return true;
    }

    /**
     * Make sure the journey hasn't already been started by the user. There could potentially be a few journeys (of the
     * same trip) that have been completed. An ongoing journey is one with no end date.
     */
    private static boolean isJourneyOngoing(Request request, String tripId) {
        var trackedJourney = getOngoingTrackedJourney(tripId);
        if (trackedJourney != null) {
            logMessageAndHalt(
                request,
                HttpStatus.FORBIDDEN_403,
                "A journey of this trip has already been started. End the current journey before starting another."
            );
            return true;
        }
        return false;
    }

    /**
     * Get active, tracked journey, based on the tracked journey id. If the end time is populated the journey has
     * already been completed.
     */
    private static TrackedJourney getActiveJourney(Request request, String trackedJourneyId) {
        var trackedJourney = Persistence.trackedJourneys.getById(trackedJourneyId);
        if (trackedJourney != null && trackedJourney.endTime == null) {
            return trackedJourney;
        } else {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Provided journey does not exist or has already been completed!");
            return null;
        }
    }

    /**
     * Get active, tracked journey, based on the trip id.
     */
    private static TrackedJourney getActiveJourneyForTripId(Request request, String tripId) {
        var trackedJourney = getOngoingTrackedJourney(tripId);
        if (trackedJourney != null) {
            return trackedJourney;
        } else {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Journey for provided trip id does not exist!");
            return null;
        }
    }

    /**
     * Get the ongoing tracked journey for trip id.
     */
    private static TrackedJourney getOngoingTrackedJourney(String tripId) {
        return Persistence.trackedJourneys.getOneFiltered(
            Filters.and(
                eq(TrackedJourney.TRIP_ID_FIELD_NAME, tripId),
                eq(TrackedJourney.END_TIME_FIELD_NAME, null)
            )
        );
    }
}
