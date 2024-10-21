package org.opentripplanner.middleware.triptracker;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import spark.Request;

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
    public static TrackingResponse startTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
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
                return doUpdateTracking(request, tripData, true);
            }
        }
        return null;
    }

    private static TrackingResponse doUpdateTracking(Request request, TripTrackingData tripData, boolean create) {
        try {
            TrackedJourney trackedJourney;
            if (create) {
                trackedJourney = new TrackedJourney(tripData.trip.id, tripData.locations.get(0));
            } else {
                trackedJourney = tripData.journey;
                trackedJourney.update(tripData.locations);
            }

            TravelerPosition travelerPosition = new TravelerPosition(
                trackedJourney,
                tripData.trip.journeyState.matchingItinerary,
                Persistence.otpUsers.getById(tripData.trip.userId)
            );
            TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
            trackedJourney.lastLocation().tripStatus = tripStatus;
            trackedJourney.lastLocation().deviationMeters = travelerPosition.getDeviationMeters();

            if (create) {
                Persistence.trackedJourneys.create(trackedJourney);
            } else {
                Persistence.trackedJourneys.updateField(
                    trackedJourney.id,
                    TrackedJourney.LOCATIONS_FIELD_NAME,
                    trackedJourney.locations
                );
            }

            // Provide response.
            return new TrackingResponse(
                TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                TravelerLocator.getInstruction(tripStatus, travelerPosition, create),
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
        TripTrackingData tripData = TripTrackingData.fromRequestJourneyId(request);
        if (tripData != null) {
            return doUpdateTracking(request, tripData, false);
        }
        return null;
    }

    /**
     * Update the tracking location information provided by the caller.
     */
    public static TrackingResponse startOrUpdateTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
        if (tripData != null) {
            return doUpdateTracking(request, tripData, tripData.journey == null);
        }
        return null;
    }

    /**
     * End tracking by saving the end condition and date.
     */
    public static EndTrackingResponse endTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestJourneyId(request);
        if (tripData != null) {
            return completeJourney(tripData, false);
        }
        return null;
    }

    /**
     * Forcibly end tracking based on the trip id. This is to be used only when the journey id is unknown and the end
     * tracking request can not be made. This prevents the scenario of a journey being 'lost' and the user not been able
     * to restart it.
     */
    public static EndTrackingResponse forciblyEndTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
        if (tripData != null) {
            if (tripData.journey != null) {
                return completeJourney(tripData, true);
            } else {
                logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Journey for provided trip id does not exist!");
                return null;
            }
        }
        return null;
    }

    /**
     * Complete a journey by defining the ending type, time and condition. Also cancel possible upcoming bus
     * notification.
     */
    private static EndTrackingResponse completeJourney(TripTrackingData tripData, boolean isForciblyEnded) {
        TravelerPosition travelerPosition = new TravelerPosition(
            tripData.journey,
            tripData.trip.journeyState.matchingItinerary,
            Persistence.otpUsers.getById(tripData.trip.userId)
        );
        BusOperatorActions
            .getDefault()
            .handleCancelNotificationAction(travelerPosition);
        TrackedJourney trackedJourney = travelerPosition.trackedJourney;
        trackedJourney.end(isForciblyEnded);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_TIME_FIELD_NAME, trackedJourney.endTime);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_CONDITION_FIELD_NAME, trackedJourney.endCondition);
        trackedJourney.longestConsecutiveDeviatedPoints = trackedJourney.computeLargestConsecutiveDeviations();
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.LONGEST_CONSECUTIVE_DEVIATED_POINTS_FIELD_NAME, trackedJourney.longestConsecutiveDeviatedPoints);

        // Provide response.
        return new EndTrackingResponse(
            TripInstruction.NO_INSTRUCTION,
            TripStatus.ENDED.name()
        );

    }
}
