package org.opentripplanner.middleware.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingPayload;
import org.opentripplanner.middleware.triptracker.TripStage;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;
import spark.Request;

import java.util.Objects;

import static com.mongodb.client.model.Filters.eq;
import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Controller to track trips. This secure end point will allow authorized users to start, update and end trip tracking
 * for monitored trips associated to them.
 */
public class TrackedTripController implements Endpoint {

    private final String path;

    private static final String SECURE = "secure/";

    public TrackedTripController(String apiPrefix) {
        this.path = apiPrefix + SECURE + "monitoredtrip";
    }

    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
                endpointPath(path).withDescription("Interface for tracking monitored trips."),
                HttpUtils.NO_FILTER
            )
            .post(path("/starttracking")
                    .withDescription("Initiates the tracking of a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> trackTrip(request, TripStage.START), JsonUtils::toJson)
            .post(path("/updatetracking")
                    .withDescription("Provides tracking updates on a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> trackTrip(request, TripStage.UPDATE), JsonUtils::toJson)
            .post(path("/endtracking")
                    .withDescription("Terminates the tracking of a monitored trip by the user.")
                    .withProduces(JSON_ONLY)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> trackTrip(request, TripStage.END), JsonUtils::toJson);
    }

    /**
     * Provide the correct response to the caller based on the trip stage.
     */
    private static Object trackTrip(Request request, TripStage tripStage) {
        TrackingPayload payload = getTrackingPayloadFromRequest(request);
        if (payload != null) {
            switch (tripStage) {
                case START:
                    if (isTripAssociatedWithUser(request, payload.tripId)) {
                        return ManageTripTracking.startTrackingTrip(payload);
                    }
                    break;
                case UPDATE:
                    return ManageTripTracking.updateTracking(
                        payload,
                        Objects.requireNonNull(getActiveJourney(request, payload)
                    ));
                case END:
                    ManageTripTracking.endTracking(Objects.requireNonNull(getActiveJourney(request, payload)));
                    break;
                default:
                    logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Unknown trip stage: " + tripStage);
                    return null;
            }
        }
        return null;
    }

    /**
     * Get the expected tracking payload for the request. The populated contents of the payload will be determined by
     * the trip stage.
     */
    private static TrackingPayload getTrackingPayloadFromRequest(Request request) {
        TrackingPayload payload;
        try {
            payload = getPOJOFromRequestBody(request, TrackingPayload.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON tracking payload.", e);
            return null;
        }
        return payload;
    }

    /**
     * Confirm that the monitored trip (that the user is on) belongs to them.
     */
    private static boolean isTripAssociatedWithUser(Request request, String tripId) {
        RequestingUser user = Auth0Connection.getUserFromRequest(request);

        MonitoredTrip monitoredTrip = Persistence.monitoredTrips.getById(tripId);
        if (
            monitoredTrip == null ||
            (user.adminUser != null && !monitoredTrip.userId.equals(user.adminUser.id)) ||
            (user.otpUser != null && !monitoredTrip.userId.equals(user.otpUser.id)) ||
            (user.apiUser != null && !monitoredTrip.userId.equals(user.apiUser.id))
        ) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Monitored trip is not associated with this user!");
            return false;
        }
        return true;
    }

    /**
     * Get active, tracked journey, based on the journey id. If the end time is populated the journey has already been
     * completed.
     */
    private static TrackedJourney getActiveJourney(Request request, TrackingPayload payload) {
        TrackedJourney trackedJourney = Persistence.trackedJourneys.getOneFiltered(eq("journeyId", payload.journeyId));
        if (trackedJourney != null && trackedJourney.endTime == null) {
            return trackedJourney;
        } else {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Provided journey does not exist or has already been completed!");
            return null;
        }
    }
}
