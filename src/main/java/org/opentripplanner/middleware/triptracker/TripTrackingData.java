package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.payload.GeneralPayload;
import spark.Request;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Helper class that holds a {@link MonitoredTrip}, {@link TrackedJourney}, and a list of {@link TrackingLocation}
 * involved with the trip tracking endpoints.
 */
public class TripTrackingData {
    public final MonitoredTrip trip;
    public final TrackedJourney journey;
    public final List<TrackingLocation> locations;

    private TripTrackingData(MonitoredTrip trip, TrackedJourney journey, List<TrackingLocation> locations) {
        this.trip = trip;
        this.journey = journey;
        this.locations = locations;
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
    public static TrackedJourney getOngoingTrackedJourney(String tripId) {
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

    /** Obtain trip, journey if any, and locations from the trip id contained in the request. */
    public static TripTrackingData fromRequestTripId(Request request) {
        GeneralPayload payload = getPayloadFromRequest(request);
        if (payload != null) {
            var monitoredTrip = Persistence.monitoredTrips.getById(payload.tripId);
            if (isTripAssociatedWithUser(request, monitoredTrip)) {
                List<TrackingLocation> locationsMillis = getTrackingLocationsMillis(payload);
                return new TripTrackingData(monitoredTrip, getOngoingTrackedJourney(payload.tripId), locationsMillis);
            }
        }
        return null;
    }

    /** HACK: Convert locations so that the time stamp is in milliseconds not seconds. */
    private static List<TrackingLocation> getTrackingLocationsMillis(GeneralPayload payload) {
        return payload
            .getLocations()
            .stream()
            .map(l -> new TrackingLocation(l.bearing, l.lat, l.lon, l.speed, Date.from(Instant.ofEpochMilli(l.timestamp.getTime() * 1000))))
            .collect(Collectors.toList());
    }

    /** Obtain trip, journey, and locations from the journey id contained in the request. */
    public static TripTrackingData fromRequestJourneyId(Request request) {
        GeneralPayload payload = getPayloadFromRequest(request);
        if (payload != null) {
            var trackedJourney = getActiveJourney(request, payload.journeyId);
            if (trackedJourney != null) {
                var monitoredTrip = Persistence.monitoredTrips.getById(trackedJourney.tripId);
                if (isTripAssociatedWithUser(request, monitoredTrip)) {
                    return new TripTrackingData(monitoredTrip, trackedJourney, payload.getLocations());
                }
            }
        }
        return null;
    }
}
