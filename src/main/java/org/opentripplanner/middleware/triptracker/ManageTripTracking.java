package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.payload.GeneralPayload;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import spark.Request;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;
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
    public static TrackingResponse startTracking(Request request) {
        TripData tripData = getTripAndJourneyForUser(request);
        if (tripData != null) {
            if (tripData.journey != null) {
                // Make sure the journey hasn't already been started by the user. There could potentially be a few
                // journeys (of the same trip) that have been completed.
                // An ongoing journey is one with no end date.
                logMessageAndHalt(
                    request,
                    HttpStatus.FORBIDDEN_403,
                    "A journey of this trip has already been started. End the current journey before starting another."
                );
            } else {
                return startTracking(request, tripData);
            }
        }
        return null;
    }

    private static TrackingResponse startTracking(Request request, TripData tripData) {
        try {
            // Start tracking journey.
            var trackedJourney = new TrackedJourney(tripData.trip.id, tripData.locations.get(0));
            TravelerPosition travelerPosition = new TravelerPosition(
                trackedJourney,
                tripData.trip.journeyState.matchingItinerary
            );
            TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
            trackedJourney.lastLocation().tripStatus = tripStatus;
            Persistence.trackedJourneys.create(trackedJourney);
            // Provide response.
            return new TrackingResponse(
                TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                TravelerLocator.getInstruction(tripStatus, travelerPosition, true),
                trackedJourney.id,
                tripStatus.name()
            );
        } catch (UnsupportedOperationException e) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
        }
        return null;
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static TrackingResponse updateTracking(Request request) {
        TripData tripData = getJourneyAndTripForUser(request);
        if (tripData != null) {
            return updateTracking(request, tripData);
        }
        return null;
    }

    private static TrackingResponse updateTracking(Request request, TripData tripData) {
        try {
            TrackedJourney trackedJourney = tripData.journey;
            TravelerPosition travelerPosition = new TravelerPosition(
                trackedJourney,
                tripData.trip.journeyState.matchingItinerary
            );
            // Update tracked journey.
            trackedJourney.update(tripData.locations);
            TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
            trackedJourney.lastLocation().tripStatus = tripStatus;
            Persistence.trackedJourneys.updateField(
                trackedJourney.id,
                TrackedJourney.LOCATIONS_FIELD_NAME,
                trackedJourney.locations
            );
            // Provide response.
            return new TrackingResponse(
                TravelerLocator.getInstruction(tripStatus, travelerPosition, false),
                tripStatus.name()
            );
        } catch (UnsupportedOperationException e) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
        }
        return null;
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static TrackingResponse startOrUpdateTracking(Request request) {
        TripData tripData = getTripAndJourneyForUser(request);
        if (tripData != null) {
            if (tripData.journey != null) {
                return updateTracking(request, tripData);
            } else {
                return startTracking(request, tripData);
            }
        }
        return null;
    }

    /**
     * End tracking by saving the end condition and date.
     */
    public static EndTrackingResponse endTracking(Request request) {
        TripData tripData = getJourneyAndTripForUser(request);
        if (tripData != null) {
            return completeJourney(tripData.journey, false);
        }
        return null;
    }

    /**
     * Forcibly end tracking based on the trip id. This is to be used only when the journey id is unknown and the end
     * tracking request can not be made. This prevents the scenario of a journey being 'lost' and the user not been able
     * to restart it.
     */
    public static EndTrackingResponse forciblyEndTracking(Request request) {
        TripData tripData = getTripAndJourneyForUser(request);
        if (tripData != null) {
            if (tripData.journey != null) {
                return completeJourney(tripData.journey, true);
            } else {
                logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Journey for provided trip id does not exist!");
                return null;
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
            TripInstruction.NO_INSTRUCTION,
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
    private static GeneralPayload getPayloadFromRequest(Request request) {
        try {
            return getPOJOFromRequestBody(request, GeneralPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON tracking payload.", e);
            return null;
        }
    }

    private static class TripData {
        public final MonitoredTrip trip;
        public final TrackedJourney journey;
        public final List<TrackingLocation> locations;

        public TripData(MonitoredTrip trip, TrackedJourney journey, List<TrackingLocation> locations) {
            this.trip = trip;
            this.journey = journey;
            this.locations = locations;
        }
    }

    private static TripData getTripAndJourneyForUser(Request request) {
        GeneralPayload payload = getPayloadFromRequest(request);
        if (payload != null) {
            var monitoredTrip = Persistence.monitoredTrips.getById(payload.tripId);
            if (isTripAssociatedWithUser(request, monitoredTrip)) {
                return new TripData(monitoredTrip, getOngoingTrackedJourney(payload.tripId), payload.getLocations());
            }
        }
        return null;
    }

    private static TripData getJourneyAndTripForUser(Request request) {
        GeneralPayload payload = getPayloadFromRequest(request);
        if (payload != null) {
            var trackedJourney = getActiveJourney(request, payload.journeyId);
            if (trackedJourney != null) {
                var monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
                if (isTripAssociatedWithUser(request, monitoredTrip)) {
                    return new TripData(monitoredTrip, trackedJourney, payload.getLocations());
                }
            }
        }
        return null;
    }
}
