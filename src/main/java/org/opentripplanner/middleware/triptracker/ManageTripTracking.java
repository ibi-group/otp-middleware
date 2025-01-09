package org.opentripplanner.middleware.triptracker;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.LegTransitionNotification;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.OtpVersion;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.instruction.SelfLegInstruction;
import org.opentripplanner.middleware.triptracker.interactions.TripActions;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import spark.Request;

import java.time.LocalDateTime;

import static org.opentripplanner.middleware.otp.response.Itinerary.getShortestDuration;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isAtStartOfLeg;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getTimeNowAsString;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteGtfsIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.isBusLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.legsMatch;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class ManageTripTracking {

    private ManageTripTracking() {
    }

    public static boolean IS_TEST = false;

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
                Persistence.otpUsers.getById(tripData.trip.getPrimaryTravelerId())
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

            LegTransitionNotification.checkForLegTransition(tripStatus, travelerPosition, tripData.trip);

            // Provide response.
            TripInstruction instruction = TravelerLocator.getInstruction(tripStatus, travelerPosition, create);

            // Perform interactions such as triggering traffic signals when approaching segments so configured.
            // It is assumed to be ok to repeatedly perform the interaction.
            if (instruction instanceof SelfLegInstruction && instruction.distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS) {
                TripActions.getDefault().handleSegmentAction(
                    ((SelfLegInstruction)instruction).getLegStep(),
                    travelerPosition.expectedLeg.steps,
                    Persistence.otpUsers.getById(tripData.trip.getPrimaryTravelerId())
                );
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
     * Attempt to reroute trip and provide appropriate response.
     */
    public static TrackingResponse rerouteTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
        if (tripData != null) {
            var trackedJourney = tripData.journey;
            if (trackedJourney != null) {
                if (rerouteTrip(tripData, false)) {
                    return doUpdateTracking(request, tripData, false);
                } else {
                    return new TrackingResponse(
                        TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                        "No itinerary found!",
                        trackedJourney.id,
                        TripStatus.DEVIATED.name()
                    );
                }
            } else {
                logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Journey for provided trip id does not exist!");
                return null;
            }
        }
        return null;
    }

    /**
     * Attempt to reroute from the traveler's current location to the trip's original destination.
     */
    public static boolean rerouteTrip(TripTrackingData tripData, boolean isTest) {
        IS_TEST = isTest;
        if (tripData != null) {
            var trackedJourney = tripData.journey;
            return trackedJourney != null && updateTripWithNewItinerary(trackedJourney);
        }
        return false;
    }

    /**
     * Record the location and time (as Strings to appease Mongo) a trip was rerouted and reset deviations.
     */
    private static void registerRerouting(TrackedJourney trackedJourney) {
        trackedJourney.longestConsecutiveDeviatedPoints = -1;
        trackedJourney.reroutings.put(
            new Coordinates(trackedJourney.lastLocation()).getCoordinates(),
            LocalDateTime.now().toString()
        );
        Persistence.trackedJourneys.replace(trackedJourney.id, trackedJourney);
    }

    /**
     * Retrieve a new itinerary from OTP and update the associated trip's matching itinerary.
     */
    private static boolean updateTripWithNewItinerary(TrackedJourney trackedJourney) {
        Itinerary itinerary = getItineraryFromOtpResponse(trackedJourney);
        if (itinerary != null) {
            trackedJourney.trip.journeyState.matchingItinerary = itinerary;
            Persistence.monitoredTrips.replace(trackedJourney.trip.id, trackedJourney.trip);
            registerRerouting(trackedJourney);
            return true;
        }
        return false;
    }

    /**
     * Get the itinerary with the shortest duration returned from OTP using the new start location and current time.
     */
    private static Itinerary getItineraryFromOtpResponse(TrackedJourney trackedJourney) {
        if (IS_TEST) {
            // return the original trip itinerary which for testing purposes will differ from the matching itinerary.
            return trackedJourney.trip.itinerary;
        }
        try {
            OtpGraphQLVariables query = trackedJourney.trip.otp2QueryParams;
            query.fromPlace = new Coordinates(trackedJourney.lastLocation()).getCoordinates();
            query.time = getTimeNowAsString();
            OtpDispatcherResponse response = OtpDispatcher.sendOtpPlanRequest(
                OtpVersion.OTP2,
                trackedJourney.trip.otp2QueryParams
            );
            TripPlan plan = response.getOtp2Response().plan;
            return plan == null ? null : getShortestDuration(plan.itineraries);
        } catch (Exception e) {
            return null;
        }
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
        cancelBusNotification(travelerPosition);
        TrackedJourney trackedJourney = travelerPosition.trackedJourney;
        trackedJourney.end(isForciblyEnded);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_TIME_FIELD_NAME, trackedJourney.endTime);
        Persistence.trackedJourneys.updateField(trackedJourney.id, TrackedJourney.END_CONDITION_FIELD_NAME, trackedJourney.endCondition);
        trackedJourney.longestConsecutiveDeviatedPoints = trackedJourney.computeLargestConsecutiveDeviations();
        Persistence.trackedJourneys.updateField(
            trackedJourney.id,
            TrackedJourney.LONGEST_CONSECUTIVE_DEVIATED_POINTS_FIELD_NAME,
            trackedJourney.longestConsecutiveDeviatedPoints
        );

        return new EndTrackingResponse(
            NO_INSTRUCTION,
            TripStatus.ENDED.name()
        );
    }

    /**
     * Cancel bus notifications which are no longer needed/relevant.
     */
    private static void cancelBusNotification(TravelerPosition travelerPosition) {
        Leg busLeg = travelerPosition.nextLeg;
        if (shouldCancelBusNotificationForStartOfTrip(travelerPosition)) {
            busLeg = travelerPosition.expectedLeg;
        }
        BusOperatorActions
            .getDefault()
            .handleCancelNotificationAction(travelerPosition, busLeg);
    }

    /**
     * Traveler is still waiting to board the bus at the start of a trip and notification has been sent.
     */
    public static boolean shouldCancelBusNotificationForStartOfTrip(TravelerPosition travelerPosition) {
        return hasSentBusNotificationForStartOfTrip(travelerPosition) && isWaitingForBusAtStartOfTrip(travelerPosition);
    }

    /**
     * Bus notification has been sent for the start of the trip.
     */
    private static boolean hasSentBusNotificationForStartOfTrip(TravelerPosition travelerPosition) {
        var routeId = getRouteGtfsIdFromLeg(travelerPosition.expectedLeg);
        return routeId != null && travelerPosition.trackedJourney.busNotificationMessages.containsKey(routeId);
    }

    /**
     * Traveler is waiting for a bus at the start of a trip.
     */
    private static boolean isWaitingForBusAtStartOfTrip(TravelerPosition travelerPosition) {
        return
            legsMatch(travelerPosition.expectedLeg, travelerPosition.firstLegOfTrip) &&
            isBusLeg(travelerPosition.expectedLeg) &&
            isAtStartOfLeg(travelerPosition);
    }
}