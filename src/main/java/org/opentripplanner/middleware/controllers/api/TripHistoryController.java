package org.opentripplanner.middleware.controllers.api;

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
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthorized;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for processing trip history related requests provided by MOD UI.
 * To provide a response to the calling MOD UI in JSON based on the passed in parameters.
 */
public class TripHistoryController {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryController.class);

    private static final String FROM_DATE_PARAM_NAME = "fromDate";
    private static final String TO_DATE_PARAM_NAME = "toDate";
    private static final String LIMIT_PARAM_NAME = "limit";
    private static final int DEFAULT_LIMIT = 10;

    /**
     * Return JSON representation of a user's trip request history based on provided parameters.
     * An authorized user (Auth0) and user id are required.
     */
    public static String getTripRequests(Request request, Response response) {

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
                String.format("%s (%s) before %s (%s)", TO_DATE_PARAM_NAME , paramToDate, FROM_DATE_PARAM_NAME,
                    paramFromDate));
        }

        Bson filter = buildFilter(userId, fromDate, toDate);
        return JsonUtils.toJson(tripRequest.getFilteredWithLimit(filter, limit));
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

        String expectedDatePattern = "yyyy-MM-dd";
        LocalDate localDate = null;
        try {
            localDate = DateUtils.getDateFromParam(paramName, paramValue, expectedDatePattern);
        } catch (DateTimeParseException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400,
                String.format("%s value: %s is not a valid date. Must be in the format: %s", paramName, paramValue,
                    expectedDatePattern));
        }

        if (localDate == null) {
            return null;
        }

        return Date.from(localDate.atTime(timeOfDay)
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
