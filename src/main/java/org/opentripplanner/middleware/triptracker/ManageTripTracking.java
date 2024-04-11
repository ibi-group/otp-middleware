package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.StartTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.UpdateTrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getExpectedLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromPosition;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromTime;
import static org.opentripplanner.middleware.triptracker.TripInstruction.getInstructions;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
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
        StartTrackingPayload payload = getPayloadFromRequest(request, StartTrackingPayload.class);
        if (payload != null) {
            var monitoredTrip = Persistence.monitoredTrips.getById(payload.tripId);
            if (isTripAssociatedWithUser(request, monitoredTrip) && !isJourneyOngoing(request, payload.tripId)) {
                try {
                    // Start tracking journey.
                    var trackedJourney = new TrackedJourney(payload);
                    TripStatus status = getTripStatus(trackedJourney, monitoredTrip.itinerary);
                    trackedJourney.lastLocation().tripStatus = status;
                    Persistence.trackedJourneys.create(trackedJourney);
                    // Provide response.
                    return new StartTrackingResponse(
                        TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                        getInstructions(TripStage.START),
                        trackedJourney.id,
                        status.name()
                    );
                } catch (UnsupportedOperationException e) {
                    logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static UpdateTrackingResponse updateTracking(Request request) {
        UpdatedTrackingPayload payload = getPayloadFromRequest(request, UpdatedTrackingPayload.class);
        if (payload != null) {
            var trackedJourney = getActiveJourney(request, payload.journeyId);
            if (trackedJourney != null) {
                var monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
                if (isTripAssociatedWithUser(request, monitoredTrip)) {
                    try {
                        // Update tracked journey.
                        trackedJourney.update(payload);
                        TripStatus status = getTripStatus(trackedJourney, monitoredTrip.itinerary);
                        trackedJourney.lastLocation().tripStatus = status;
                        Persistence.trackedJourneys.updateField(
                            trackedJourney.id,
                            TrackedJourney.LOCATIONS_FIELD_NAME,
                            trackedJourney.locations
                        );

                        // Provide response.
                        return new UpdateTrackingResponse(
                            // This is to be expanded on in later PRs. For now, it is used for unit testing.
                            TripInstruction.NO_INSTRUCTION.name(),
                            status.name()
                        );
                    } catch (UnsupportedOperationException e) {
                        logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    /**
     * End tracking by saving the end condition and date.
     */
    public static EndTrackingResponse endTracking(Request request) {
        EndTrackingPayload payload = getPayloadFromRequest(request, EndTrackingPayload.class);
        if (payload != null) {
            TrackedJourney trackedJourney = getActiveJourney(request, payload.journeyId);
            if (trackedJourney != null) {
                var monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
                if (isTripAssociatedWithUser(request, monitoredTrip)) {
                    return completeJourney(trackedJourney, false);
                }
            }
        }
        return null;
    }

    /**
     * Forcibly end tracking based on the trip id. This is to be used only when the journey id is unknown and the end
     * tracking request can not be made. This prevents the scenario of a journey being 'lost' and the user not been able
     * to restart it.
     */
    public static EndTrackingResponse forciblyEndTracking(Request request) {
        ForceEndTrackingPayload payload = getPayloadFromRequest(request, ForceEndTrackingPayload.class);
        if (payload != null) {
            TrackedJourney trackedJourney = getActiveJourneyForTripId(request, payload.tripId);
            if (trackedJourney != null) {
                var monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
                if (isTripAssociatedWithUser(request, monitoredTrip)) {
                    return completeJourney(trackedJourney, true);
                }
            }
        }
        return null;
    }

    /**
     * Complete a journey by defining the ending type, time and condition.
     */
    private static EndTrackingResponse completeJourney(TrackedJourney trackedJourney, boolean isForciblyEnded) {
        trackedJourney.end(isForciblyEnded);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_TIME_FIELD_NAME, trackedJourney.endTime);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_CONDITION_FIELD_NAME, trackedJourney.endCondition);

        // Provide response.
        return new EndTrackingResponse(
            getInstructions(isForciblyEnded ? TripStage.FORCE_END : TripStage.END),
            TripStatus.ENDED.name()
        );

    }

    /**
     * Confirm that the monitored trip that the user is on belongs to them.
     */
    private static boolean isTripAssociatedWithUser(Request request, MonitoredTrip monitoredTrip) {
        var user = Auth0Connection.getUserFromRequest(request);

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

    /**
     * Get the expected tracking payload for the request.
     */
    private static <T> T getPayloadFromRequest(Request request, Class<T> payloadClass) {
        try {
            return getPOJOFromRequestBody(request, payloadClass);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON tracking payload.", e);
            return null;
        }
    }

    /**
     * Get the trip status by comparing the traveler's position to expected and nearest positions to the trip route.
     */
    public static TripStatus getTripStatus(TrackedJourney trackedJourney, Itinerary itinerary) {
        TrackingLocation lastLocation = trackedJourney.locations.get(trackedJourney.locations.size() - 1);
        Coordinates currentPosition = new Coordinates(lastLocation);
        var expectedLeg = getExpectedLeg(lastLocation.timestamp.toInstant(), itinerary);
        return TripStatus.getTripStatus(
            currentPosition,
            lastLocation.timestamp.toInstant(),
            expectedLeg,
            getSegmentFromTime(lastLocation.timestamp.toInstant(), itinerary),
            getSegmentFromPosition(expectedLeg, currentPosition)
        );
    }
}
