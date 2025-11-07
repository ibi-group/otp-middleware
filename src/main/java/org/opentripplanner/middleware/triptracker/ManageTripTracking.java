package org.opentripplanner.middleware.triptracker;

import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.opentripplanner.middleware.itinerarymatching.LegMatcher;
import org.opentripplanner.middleware.models.LegTransitionNotification;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction;
import org.opentripplanner.middleware.triptracker.instruction.SelfLegInstruction;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.triptracker.interactions.TripActions;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.triptracker.response.EndTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.RerouteResponse;
import org.opentripplanner.middleware.triptracker.response.TrackingResponse;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import spark.Request;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

import static org.opentripplanner.middleware.i18n.Message.TRIP_REROUTED_NOTIFICATION;
import static org.opentripplanner.middleware.otp.response.Itinerary.getShortestDuration;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isAtStartOfLeg;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getTimeNowAsString;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteGtfsIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.isBusLeg;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class ManageTripTracking {

    public static Supplier<OtpResponse> otpResponseProviderOverride = null;
    private static OtpGraphQLVariables rerouteVariables = null;

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

    private static TrackingResponse doUpdateTracking(
        Request request,
        TripTrackingData tripData,
        boolean create
    ) {
        try {
            TrackedJourney trackedJourney;
            if (create) {
                trackedJourney = new TrackedJourney(tripData.trip.id, tripData.locations.get(0));
            } else {
                trackedJourney = tripData.journey;
                trackedJourney.update(tripData.locations);
            }

            Itinerary matchingItinerary = tripData.trip.journeyState.matchingItinerary;
            TravelerPosition travelerPosition = new TravelerPosition(
                trackedJourney,
                matchingItinerary,
                Persistence.otpUsers.getById(tripData.trip.getPrimaryTravelerId())
            );
            TripStatus tripStatus = TripStatus.NO_ITINERARY;

            boolean isValidItinerary = matchingItinerary != null &&
                tripData.trip.journeyState.tripStatus != org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
            if (isValidItinerary) {
                tripStatus = TripStatus.getTripStatus(travelerPosition);
                trackedJourney.lastLocation().deviationMeters = travelerPosition.getDeviationMeters();
            }
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

            if (!isValidItinerary) {
                return new TrackingResponse(
                    TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                    "Unable to monitor trip.",
                    trackedJourney.id,
                    tripStatus.name()
                );
            }

            LegTransitionNotification.checkForLegTransition(tripStatus, travelerPosition, tripData.trip);

            // Provide response.
            TripInstruction instruction = TravelerLocator.getInstruction(tripStatus, travelerPosition);

            if (isDeviatedWithOnTrackInstruction(tripStatus, instruction)) {
                // Deem traveler on track (not deviated) if they are in the 'upcoming' range of a bus stop
                // or the departure location.
                // (If near a bus stop, applicable bus notifications would have already been triggered.)
                tripStatus = TripStatus.getTimingStatus(travelerPosition);
            }

            if (isEndOfRoutingInstruction(instruction) && travelerPosition.nextLeg == null) {
                // Deem trip completed if on the last leg and issuing a "destination in vicinity" instruction.
                tripStatus = TripStatus.COMPLETED;
            }

            if (instruction instanceof WaitForTransitInstruction) {
                // Deem trip ahead/on-time/behind depending on departure time of transit leg.
                TravelerPosition adjustedPosition = new TravelerPosition.Builder()
                    .setExpectedLeg(travelerPosition.nextLeg)
                    .setCurrentPosition(travelerPosition.currentPosition)
                    .setCurrentTime(travelerPosition.currentTime)
                    .build();
                tripStatus = TripStatus.getTimingStatus(adjustedPosition);
            }

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
     * Detect if the instruction is an end-of-routing instruction
     * (to give a 'completed' status to the trip, for instance).
     */
    private static boolean isEndOfRoutingInstruction(TripInstruction instruction) {
        if (instruction instanceof OnTrackInstruction) {
            Step step = ((OnTrackInstruction) instruction).getLegStep();
            if (step != null) {
                return step.isEndOfRouting();
            }
        }
        return false;
    }

    /**
     * Detect if we are deeming a traveler deviated while giving out a non-deviated instruction
     * (to prevent, for instance, being offered rerouting while near a bus stop).
     */
    private static boolean isDeviatedWithOnTrackInstruction(TripStatus tripStatus, TripInstruction instruction) {
        return tripStatus == TripStatus.DEVIATED &&
            (instruction instanceof WaitForTransitInstruction || instruction instanceof OnTrackInstruction);
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
    public static RerouteResponse rerouteTracking(Request request) {
        TripTrackingData tripData = TripTrackingData.fromRequestTripId(request);
        if (tripData != null) {
            var trackedJourney = tripData.journey;
            if (trackedJourney != null) {
                var reroutedItinerary = rerouteTrip(tripData);
                if (reroutedItinerary != null) {
                    return new RerouteResponse(
                        doUpdateTracking(request, tripData, false),
                        reroutedItinerary
                    );
                } else {
                    return new RerouteResponse(
                        TRIP_TRACKING_UPDATE_FREQUENCY_SECONDS,
                        "No itinerary found!",
                        trackedJourney.id,
                        TripStatus.DEVIATED.name(),
                        null
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
    public static Itinerary rerouteTrip(TripTrackingData tripData) {
        if (tripData != null) {
            var trackedJourney = tripData.journey;
            if (trackedJourney != null) {
                var reroutedItinerary = updateTripWithNewItinerary(trackedJourney);
                if (reroutedItinerary != null) {
                    notifyTripRerouting(trackedJourney);
                    return reroutedItinerary;
                }
            }
        }
        return null;
    }

    /**
     * Notify companion and observers that the trip has been rerouted.
     */
    private static void notifyTripRerouting(TrackedJourney trackedJourney) {
        MonitoredTrip monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
        OtpUser tripCreator = Persistence.otpUsers.getById(monitoredTrip.userId);

        if (monitoredTrip.hasConfirmedCompanion() && monitoredTrip.companion != null) {
            OtpUser companionUser = Persistence.otpUsers.getOneFiltered(Filters.eq("email", monitoredTrip.companion.email));
            NotificationUtils.notifyCompanion(monitoredTrip, tripCreator, companionUser, TRIP_REROUTED_NOTIFICATION);
        }

        for (RelatedUser observer : monitoredTrip.observers) {
            if (observer.isConfirmed()) {
                OtpUser observerUser = Persistence.otpUsers.getOneFiltered(Filters.eq("email", observer.email));
                NotificationUtils.notifyCompanion(monitoredTrip, tripCreator, observerUser, TRIP_REROUTED_NOTIFICATION);
            }
        }
    }

    /**
     * Record the location and time (as Strings to appease Mongo) a trip was rerouted and reset deviations.
     */
    private static void registerRerouting(TrackedJourney trackedJourney) {
        trackedJourney.reroutings.put(
            new Coordinates(trackedJourney.lastLocation()).getCoordinates(),
            DateTimeUtils.convertToDate(LocalDateTime.now())
        );
        Persistence.trackedJourneys.replace(trackedJourney.id, trackedJourney);
    }

    /**
     * Retrieve a new itinerary from OTP and update the associated trip's matching itinerary.
     */
    private static Itinerary updateTripWithNewItinerary(TrackedJourney trackedJourney) {
        Itinerary itinerary = getItineraryFromOtpResponse(trackedJourney);
        if (itinerary != null) {
            trackedJourney.trip.journeyState.matchingItinerary = itinerary;
            Persistence.monitoredTrips.replace(trackedJourney.trip.id, trackedJourney.trip);
            registerRerouting(trackedJourney);
            return itinerary;
        }
        return null;
    }

    /**
     * Get the itinerary with the shortest duration returned from OTP using the new start location and current time.
     */
    private static Itinerary getItineraryFromOtpResponse(TrackedJourney trackedJourney) {
        try {
            Supplier<OtpResponse> otpResponseProvider = getOtpResponseProvider();
            rerouteVariables = setOtpGraphQLVariables(
                trackedJourney.trip.otp2QueryParams, new Coordinates(trackedJourney.lastLocation())
            );
            TripPlan plan = otpResponseProvider.get().plan;
            return plan == null ? null : getShortestDuration(plan.itineraries);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Define the new reroute variables related to the traveler's current location to be passed to OTP.
     */
    public static OtpGraphQLVariables setOtpGraphQLVariables(OtpGraphQLVariables originalTripVariables, Coordinates from) {
        OtpGraphQLVariables query = originalTripVariables.clone();
        query.fromPlace = from.getCoordinates();
        query.time = getTimeNowAsString();
        return query;
    }

    /**
     * Define the OTP response source. This will either be an OTP server response or a mocked response for testing.
     */
    private static Supplier<OtpResponse> getOtpResponseProvider() {
        return OtpMiddlewareMain.inTestEnvironment && otpResponseProviderOverride != null
            ? otpResponseProviderOverride
            : ManageTripTracking::getOtpResponse;
    }

    /** Default implementation for OtpResponse provider that actually invokes the OTP server. */
    private static OtpResponse getOtpResponse() {
        return OtpDispatcher.sendOtpRequestWithErrorHandling(rerouteVariables);
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

        resetMatchingItineraryIfNeeded(trackedJourney);

        return new EndTrackingResponse(
            NO_INSTRUCTION,
            TripStatus.ENDED.name()
        );
    }

    /**
     * If rerouting occurred, then the matching itinerary changed with a starting point different from the
     * starting location in the original itinerary.
     * In that case, reset the matching itinerary, so that trip monitoring/live tracking uses the original routing.
     */
    private static void resetMatchingItineraryIfNeeded(TrackedJourney trackedJourney) {
        if (trackedJourney.getLastReroutingLocation() != null) {
            try {
                MonitoredTrip trip = trackedJourney.trip;

                trip.journeyState.matchingItinerary = trip.itinerary.clone();

                ZonedDateTime targetZonedDateTime = trip.computeTargetZonedDateTime(trip, trip.journeyState.matchingItinerary);
                trip.itinerary.offsetTimes(targetZonedDateTime);

                Persistence.monitoredTrips.replace(trip.id, trip);
            } catch (CloneNotSupportedException e) {
                // Do nothing if clone was not created.
            }
        }
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
        LegMatcher legMatcher = new LegMatcher(travelerPosition.expectedLeg, travelerPosition.firstLegOfTrip);
        return
            legMatcher.match() &&
            isBusLeg(travelerPosition.expectedLeg) &&
            isAtStartOfLeg(travelerPosition);
    }
}