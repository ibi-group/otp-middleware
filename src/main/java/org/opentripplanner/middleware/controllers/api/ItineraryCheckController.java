package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.net.URISyntaxException;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This endpoint is used by web clients or other APIs to let users know
 * whether a given itinerary exists on each day of the week.
 */
public class ItineraryCheckController implements Endpoint {
    private final String ROOT_ROUTE;

    public ItineraryCheckController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/itinerarycheck";
    }

    /**
     * Register the API endpoint and POST resource to check itinerary existence
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for checking itinerary existence."),
            HttpUtils.NO_FILTER
        ).post(path("")
                .withDescription("Returns a map of day and OTP responses for the itinerary to check.")
                .withRequestType(MonitoredTrip.class)
                .withProduces(JSON_ONLY)
                .withResponseType(ItineraryExistence.class),
            ItineraryCheckController::checkItinerary, JsonUtils::toJson);
    }

    /**
     * Check itinerary existence by making OTP requests on all days of the week.
     */
    private static ItineraryExistence checkItinerary(Request request, Response response) {
        try {
            MonitoredTrip trip = getPOJOFromRequestBody(request, MonitoredTrip.class);
            trip.initializeFromItineraryAndQueryParams();
            return ItineraryUtils.checkItineraryExistence(trip, true);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Error parsing JSON for MonitoredTrip", e);
        } catch (URISyntaxException e) { // triggered by OtpQueryUtils#getQueryParams.
            logMessageAndHalt(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error parsing the trip query parameters.",
                e
            );
        }
        // This is unreachable, but needed for java to compile (halts will be thrown before this is reached).
        return null;
    }
}
