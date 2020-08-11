package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.DateUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthorized;
import static org.opentripplanner.middleware.utils.DateUtils.YYYY_MM_DD;
import static org.opentripplanner.middleware.utils.HttpUtils.MIMETYPES_JSONONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for processing trip history related requests provided by MOD UI.
 * To provide a response to the calling MOD UI in JSON based on the passed in parameters.
 */
public class TripHistoryController implements Endpoint {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryController.class);

    private static final String FROM_DATE_PARAM_NAME = "fromDate";
    private static final String TO_DATE_PARAM_NAME = "toDate";
    private static final String LIMIT_PARAM_NAME = "limit";
    private static final int DEFAULT_LIMIT = 10;

    private final String ROOT_ROUTE;

    public TripHistoryController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/triprequests";
    }

    /**
     * Register the API endpoint and GET resource to get trip requests
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for retrieving trip requests."),
            (q, a) -> LOG.info("Received request for 'triprequests' Rest API")
        );
        apiEndpoint
            .get(path(ROOT_ROUTE)
                    .withDescription("Gets a list of all trip requests for a user.")
                    .withQueryParam()
                        .withName("userId")
                        .withRequired(true)
                        .withDescription("The OTP user for which to retrieve trip requests.").and()
                    .withQueryParam()
                        .withName(LIMIT_PARAM_NAME)
                        .withDefaultValue(String.valueOf(DEFAULT_LIMIT))
                        .withDescription("If specified, the maximum number of trip requests to return, starting from the most recent.").and()
                    .withQueryParam()
                        .withName(FROM_DATE_PARAM_NAME)
                        .withPattern(YYYY_MM_DD)
                        .withDefaultValue("The current date")
                        .withDescription(String.format(
                            "If specified, the earliest date (format %s) for which trip requests are retrieved.", YYYY_MM_DD
                        )).and()
                    .withQueryParam()
                        .withName(TO_DATE_PARAM_NAME)
                        .withPattern(YYYY_MM_DD)
                        .withDefaultValue("The current date")
                        .withDescription(String.format(
                            "If specified, the latest date (format %s) for which usage logs are retrieved.", YYYY_MM_DD
                        )).and()
                    .withProduces(MIMETYPES_JSONONLY)
                    // Note: unlike the name suggests, withResponseAsCollection does not generate an array
                    // as the return type for this method. (It does generate the type for that class nonetheless.)
                    .withResponseAsCollection(TripRequest.class),
                TripHistoryController::getTripRequests, JsonUtils::toJson)

            // Options response for CORS
            .options(path(""), (req, res) -> "");
    }

    /**
     * Return a user's trip request history based on provided parameters.
     * An authorized user (Auth0) and user id are required.
     */
    private static List<TripRequest> getTripRequests(Request request, Response response) {

        TypedPersistence<TripRequest> tripRequest = Persistence.tripRequests;

        final String userId = HttpUtils.getRequiredParamFromRequest(request, "userId", false);

        isAuthorized(userId, request);

        int limit = DEFAULT_LIMIT;

        String paramLimit = null;
        try {
            paramLimit = HttpUtils.getRequiredParamFromRequest(request, LIMIT_PARAM_NAME, true);
            if (paramLimit != null) {
                limit = Integer.parseInt(paramLimit);
                if (limit <= 0) {
                    limit = DEFAULT_LIMIT;
                }
            }
        } catch (NumberFormatException e) {
            LOG.error("Unable to parse {} value of {}. Using default limit: {}", LIMIT_PARAM_NAME,
                paramLimit, DEFAULT_LIMIT, e);
        }

        String paramFromDate = HttpUtils.getRequiredParamFromRequest(request, FROM_DATE_PARAM_NAME, true);
        Date fromDate = getDate(request, FROM_DATE_PARAM_NAME, paramFromDate, LocalTime.MIDNIGHT);

        String paramToDate = HttpUtils.getRequiredParamFromRequest(request, TO_DATE_PARAM_NAME, true);
        Date toDate = getDate(request, TO_DATE_PARAM_NAME, paramToDate, LocalTime.MAX);

        if (fromDate != null && toDate != null && toDate.before(fromDate)) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400,
                String.format("%s (%s) before %s (%s)", TO_DATE_PARAM_NAME, paramToDate, FROM_DATE_PARAM_NAME,
                    paramFromDate));
        }

        Bson filter = buildFilter(userId, fromDate, toDate);
        return tripRequest.getFilteredWithLimit(filter, limit);
    }

    /**
     * Build the filter which is passed to Mongo based on available parameters
     */
    private static Bson buildFilter(String userId, Date fromDate, Date toDate) {

        Set<Bson> clauses = new HashSet<>();

        // user id is required, so as a minimum return all trip requests for user
        clauses.add(eq("userId", userId));

        // Get all trip requests that occurred from supplied start date.
        if (fromDate != null) {
            clauses.add(gte("dateCreated", fromDate));
        }

        // Get all trip requests that occurred until the supplied end date.
        if (toDate != null) {
            clauses.add(lte("dateCreated", toDate));
        }

        return Filters.and(clauses);
    }

    /**
     * Get date from request parameter and convert to java.util.Date at a specific time of day. The date conversion
     * is based on the system time zone.
     */
    private static Date getDate(Request request, String paramName, String paramValue, LocalTime timeOfDay) {

        // no date value to work with
        if (paramValue == null) {
            return null;
        }

        LocalDate localDate = null;
        try {
            localDate = DateUtils.getDateFromParam(paramName, paramValue, YYYY_MM_DD);
        } catch (DateTimeParseException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400,
                String.format("%s value: %s is not a valid date. Must be in the format: %s", paramName, paramValue,
                    YYYY_MM_DD));
        }

        if (localDate == null) {
            return null;
        }

        return Date.from(localDate.atTime(timeOfDay)
            .atZone(ZoneId.systemDefault())
            .toInstant());
    }
}
