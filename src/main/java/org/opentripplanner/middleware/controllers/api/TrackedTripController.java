package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.model.RefModel;
import io.github.manusant.ss.model.Response;
import io.github.manusant.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TripStage;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.StartTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.UpdateTrackingResponse;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;
import spark.Request;

import java.util.Map;
import java.util.TreeMap;

import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
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

        Map<String, Response> responses  = new TreeMap<>();

        // Show the output data structure for the 200-ok response.
        RefModel refModel = new RefModel();
        refModel.set$ref(StartTrackingResponse.class.getSimpleName());
        responses.put("200", new Response().description("Successful operation"));
        responses.get("200").setResponseSchema(refModel);

        restApi.endpoint(
                endpointPath(path).withDescription("Interface for tracking monitored trips."),
                HttpUtils.NO_FILTER
            )
            .post(path("/starttracking")
                    .withDescription("Initiates the tracking of a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(StartTrackingPayload.class)
                    .withResponses(responses),
                (request, response) -> trackTrip(request, TripStage.START), JsonUtils::toJson)
            .post(path("/updatetracking")
                    .withDescription("Provides tracking updates on a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(UpdatedTrackingPayload.class)
                    .withResponses(SwaggerUtils.createStandardResponses(UpdateTrackingResponse.class)),
                (request, response) -> trackTrip(request, TripStage.UPDATE), JsonUtils::toJson)
            .post(path("/endtracking")
                    .withDescription("Terminates the tracking of a monitored trip by the user.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(EndTrackingPayload.class)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> trackTrip(request, TripStage.END), JsonUtils::toJson)
            .post(path("/forciblyendtracking")
                    .withDescription("Forcibly terminates tracking of a monitored trip by trip ID.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(ForceEndTrackingPayload.class)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> trackTrip(request, TripStage.FORCE_END), JsonUtils::toJson);
    }

    /**
     * Provide the correct response to the caller based on the trip stage.
     */
    private static Object trackTrip(Request request, TripStage tripStage) {
        switch (tripStage) {
            case START:
                return ManageTripTracking.startTracking(request);
            case UPDATE:
                return ManageTripTracking.updateTracking(request);
            case END:
                ManageTripTracking.endTracking(request);
                break;
            case FORCE_END:
                ManageTripTracking.forciblyEndTracking(request);
                break;
            default:
                logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Unknown trip stage: " + tripStage);
                return null;
        }
        return null;
    }
}
