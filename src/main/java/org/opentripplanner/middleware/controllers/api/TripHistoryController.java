package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.OtpUser;
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

import static com.mongodb.client.model.Filters.*;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthorized;
import static org.opentripplanner.middleware.auth.Auth0Connection.isValidUser;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for processing trip history related requests provided by MOD UI.
 * To provide a response to the calling MOD UI in JSON based on the passed in parameters.
 */
public class TripHistoryController {

    private static final Logger LOG = LoggerFactory.getLogger(TripHistoryController.class);

    private static final String USER_ID_PARAM_NAME = "userId";
    private static final String FROM_DATE_PARAM_NAME = "fromDate";
    private static final String TO_DATE_PARAM_NAME = "toDate";
    private static final String LIMIT_PARAM_NAME = "limit";
    private static final int DEFAULT_LIMIT = 10;

    /**
     * Return JSON representation of a user's trip request history based on provided parameters.
     * An authorized user (Auth0) and user id are required.
     */
    public static String getTripRequests(Request request, Response response, TypedPersistence<TripRequest> tripRequest) {

        final String userId = HttpUtils.getParamFromRequest(request, USER_ID_PARAM_NAME, false);

        OtpUser user = Persistence.otpUsers.getById(userId);
        if (user == null) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unknown user.");
        }

        isValidUser(request);
        isAuthorized(userId, request);

        int limit = DEFAULT_LIMIT;

        String paramLimit = null;
        try {
            paramLimit = HttpUtils.getParamFromRequest(request, LIMIT_PARAM_NAME, true);
            if (paramLimit != null) {
                limit = Integer.parseInt(paramLimit);
                if (limit <= 0) {
                    limit = DEFAULT_LIMIT;
                }
            }
        } catch (NumberFormatException e) {
            LOG.error("Unable to parse {} value of {}. Using default limit: {}", LIMIT_PARAM_NAME, paramLimit, DEFAULT_LIMIT, e);
        }

        String paramFromDate = HttpUtils.getParamFromRequest(request, FROM_DATE_PARAM_NAME, true);
        Date fromDate = getDate(request, FROM_DATE_PARAM_NAME, paramFromDate, LocalTime.MIDNIGHT);

        String paramToDate = HttpUtils.getParamFromRequest(request, TO_DATE_PARAM_NAME, true);
        Date toDate = getDate(request, TO_DATE_PARAM_NAME, paramToDate, LocalTime.MAX);

        if (fromDate != null && toDate != null && toDate.before(fromDate)) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, TO_DATE_PARAM_NAME + " (" + paramToDate + ") before " + FROM_DATE_PARAM_NAME + " (" + paramFromDate + ")");
        }

        Bson filter = buildFilter(userId,fromDate, toDate);
        return JsonUtils.toJson(tripRequest.getFilteredWithLimit(filter, limit));
    }

    /**
     * Build the filter which is passed to Mongo based on available parameters
     */
    private static Bson buildFilter(String userId, Date fromDate, Date toDate) {

        final String createdFieldName = "dateCreated";
        final String userIdFieldName = "userId";

        // user id is required, so as a minimum return all trip requests for user
        Bson filter = Filters.eq(userIdFieldName, userId);

        if (fromDate != null && toDate != null) { // get all trips between start and end dates
            filter = Filters.and(
                gte(createdFieldName, fromDate),
                lte(createdFieldName, toDate),
                eq(userIdFieldName, userId));
        } else if (fromDate == null && toDate != null) { // get all trip requests to end date
            filter = Filters.and(
                lte(createdFieldName, toDate),
                eq(userIdFieldName, userId));
        } else if (fromDate != null && toDate == null) { // get all trip requests from start date
            filter = Filters.and(
                gte(createdFieldName, fromDate),
                eq(userIdFieldName, userId));
        }

        return filter;
    }

    /**
     * Get date from request parameter and convert to java.util.Date at a specific time of day
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
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, paramName + " value: " + paramValue + " is not a valid date. Must be in the format: " + expectedDatePattern);
        }

        if (localDate == null) {
            return null;
        }

        return Date.from(localDate.atTime(timeOfDay).atZone(ZoneId.systemDefault()).toInstant());
    }



}
