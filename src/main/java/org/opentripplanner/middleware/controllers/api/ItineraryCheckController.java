package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ItineraryExistenceResult;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.ItineraryExistenceChecker;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This endpoint checks is used by web clients or other APIs to let users know
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
                .withResponseType(ItineraryExistenceResult.class),
            ItineraryCheckController::checkItinerary, JsonUtils::toJson);
    }

    /**
     * Check itinerary existence by making OTP requests.
     */
    private static ItineraryExistenceResult checkItinerary(Request request, Response response) {
        ItineraryExistenceResult result = new ItineraryExistenceResult();

        try {
            MonitoredTrip trip = getPOJOFromRequestBody(request, MonitoredTrip.class);
            trip.initializeFromItineraryAndQueryParams();

            ItineraryExistenceChecker itineraryChecker = new ItineraryExistenceChecker(OtpDispatcher::sendOtpPlanRequest);
            ItineraryExistenceChecker.Result checkResult = itineraryChecker
                .checkAll(ItineraryUtils.getItineraryExistenceQueries(trip, true), trip.isArriveBy());

            // Convert the dates in the result to weekdays,
            // and fill the same-day itineraries in each day, if any.
            // Note: At this time, the endpoint checks all days of the week at once before returning a response.
            for (Map.Entry<String, OtpResponse> r : checkResult.labeledResponses.entrySet()) {
                String dateString = r.getKey();
                LocalDate date = DateTimeUtils.getDateFromString(dateString, DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN);
                if (r.getValue().plan != null) {
                    // Only keep same-day itineraries.
                    List<Itinerary> sameDayItineraries = ItineraryUtils.getSameDayItineraries(r.getValue().plan.itineraries, trip, dateString);
                    if (!sameDayItineraries.isEmpty()) {
                        switch (date.getDayOfWeek()) {
                            case MONDAY:
                                result.monday = sameDayItineraries;
                                break;
                            case TUESDAY:
                                result.tuesday = sameDayItineraries;
                                break;
                            case WEDNESDAY:
                                result.wednesday = sameDayItineraries;
                                break;
                            case THURSDAY:
                                result.thursday = sameDayItineraries;
                                break;
                            case FRIDAY:
                                result.friday = sameDayItineraries;
                                break;
                            case SATURDAY:
                                result.saturday = sameDayItineraries;
                                break;
                            case SUNDAY:
                                result.sunday = sameDayItineraries;
                                break;
                        }
                    }
                }
            }
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

        return result;
    }
}
