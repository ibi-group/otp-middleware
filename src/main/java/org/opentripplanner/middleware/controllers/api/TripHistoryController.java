package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;

import static com.mongodb.client.model.Filters.*;

/**
 * Responsible for processing trip history related requests provided by MOD UI.
 * To provide a response to the calling MOD UI in JSON based on the passed in parameters.
 */
public class TripHistoryController {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryController.class);

    public static final String USER_ID_PARAM_NAME = "userId";
    public static final String FROM_DATE_PARAM_NAME = "fromDate";
    public static final String TO_DATE_PARAM_NAME = "toDate";
    public static final String LIMIT_PARAM_NAME = "limit";
    private static final String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
    private static final String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
    private static final int DEFAULT_LIMIT = 10;

    /**
     * return JSON representation of a user's trip request history based on provided parameters
     */
    public static String getTripRequests(Request request, Response response, TypedPersistence<TripRequest> tripRequest, String expectedDatePattern) {
        response.type("application/json");

        DateTimeFormatter expectDateFormat = new DateTimeFormatterBuilder().appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.systemDefault());

        String userId = HttpUtils.getParamFromRequest(request, USER_ID_PARAM_NAME);
        int limit = DEFAULT_LIMIT;

        String param = null;
        try {
            param = HttpUtils.getParamFromRequest(request, LIMIT_PARAM_NAME);
            limit = Integer.parseInt(param);
        } catch (NumberFormatException e) {
            LOG.error("Unable to parse {} : {}. Using default limit: {}", LIMIT_PARAM_NAME, param, DEFAULT_LIMIT, e);
        }

        LocalDate fromDate = HttpUtils.getDateFromRequestParam(request, expectDateFormat, expectedDatePattern, FROM_DATE_PARAM_NAME);
        LocalDate toDate = HttpUtils.getDateFromRequestParam(request, expectDateFormat, expectedDatePattern, TO_DATE_PARAM_NAME);

        LocalDateTime fromStartOfDay = fromDate.atTime(LocalTime.MIN);
        LocalDateTime toEndOfDay = toDate.atTime(LocalTime.MAX);

        Bson filter = Filters.and(gte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(fromStartOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            lte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(toEndOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, userId));

        return JsonUtils.toJson(tripRequest.getFilteredWithLimit(filter, limit));
    }
}
