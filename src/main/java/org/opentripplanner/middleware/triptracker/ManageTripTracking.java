package org.opentripplanner.middleware.triptracker;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.interactions.SegmentsWithInteractions;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import spark.Request;

import java.util.List;

import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.TripInstruction.TRIP_INSTRUCTION_IMMEDIATE_PREFIX;
import static org.opentripplanner.middleware.triptracker.TripInstruction.TRIP_INSTRUCTION_UPCOMING_PREFIX;
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
                tripData.trip.journeyState.matchingItinerary
            );
            TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
            trackedJourney.lastLocation().tripStatus = tripStatus;

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
            TripInstruction instruction = TravelerLocator.getInstruction(tripStatus, travelerPosition, create);

            // Perform interactions such as triggering traffic signals when approaching segments so configured.
            // It is assumed to be ok to repeatedly perform the interaction.
            if (
                instruction != null && (
                    TRIP_INSTRUCTION_UPCOMING_PREFIX.equals(instruction.prefix) ||
                        TRIP_INSTRUCTION_IMMEDIATE_PREFIX.equals(instruction.prefix)
                )
            ) {
                OtpUser user = Persistence.otpUsers.getById(tripData.trip.userId);
                Step upcomingStep = instruction.legStep;
                List<Step> steps = travelerPosition.expectedLeg.steps;
                int upcomingStepIndex = steps.indexOf(upcomingStep);
                if (upcomingStepIndex < steps.size() - 1) {
                    Step stepAfter = steps.get(upcomingStepIndex + 1);
                    Segment segment = new Segment(
                        new Coordinates(upcomingStep),
                        new Coordinates(stepAfter)
                    );
                    SegmentsWithInteractions.handleSegmentAction(segment, user);
                }
            }

            return new TrackingResponse(
                TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                instruction != null ? instruction.build() : NO_INSTRUCTION,
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
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
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
            NO_INSTRUCTION,
            TripStatus.ENDED.name()
        );

    }
}
