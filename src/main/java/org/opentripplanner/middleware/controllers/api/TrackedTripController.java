package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.rest.Endpoint;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.payload.EndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.ForceEndTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.StartTrackingPayload;
import org.opentripplanner.middleware.triptracker.payload.UpdatedTrackingPayload;
import org.opentripplanner.middleware.triptracker.response.StartTrackingResponse;
import org.opentripplanner.middleware.triptracker.response.UpdateTrackingResponse;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;

import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;

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

    /**
     * There is a bug in SparkSwagger that prevents POST endpoints from having multiple responses (.withResponses) with
     * a response type class. The workaround is to use .withResponseType which will assign the provided class to the
     * 200 response. The downside is that no other response types can be defined.
     */
    @Override
    public void bind(final SparkSwagger restApi) {

        restApi.endpoint(
                endpointPath(path).withDescription("Interface for tracking monitored trips."),
                HttpUtils.NO_FILTER
            )
            .post(path("/starttracking")
                    .withDescription("Initiates the tracking of a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(StartTrackingPayload.class)
                    .withResponseType(StartTrackingResponse.class),
                (request, response) -> ManageTripTracking.startTracking(request), JsonUtils::toJson)
            .post(path("/updatetracking")
                    .withDescription("Provides tracking updates on a monitored trip.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(UpdatedTrackingPayload.class)
                    .withResponseType(UpdateTrackingResponse.class),
                (request, response) -> ManageTripTracking.updateTracking(request), JsonUtils::toJson)
            .post(path("/endtracking")
                    .withDescription("Terminates the tracking of a monitored trip by the user.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(EndTrackingPayload.class)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> ManageTripTracking.endTracking(request), JsonUtils::toJson)
            .post(path("/forciblyendtracking")
                    .withDescription("Forcibly terminates tracking of a monitored trip by trip ID.")
                    .withProduces(JSON_ONLY)
                    .withRequestType(ForceEndTrackingPayload.class)
                    .withResponses(SwaggerUtils.createStandardResponses()),
                (request, response) -> ManageTripTracking.forciblyEndTracking(request), JsonUtils::toJson);
    }
}
