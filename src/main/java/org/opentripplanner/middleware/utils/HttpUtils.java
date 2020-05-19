package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class HttpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * Get entity attribute value from request.
     */
    public static String getParamFromRequest(Request req, String paramName) {
        String paramValue = req.queryParams(paramName);
        if (paramValue == null) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide parameter name.");
        }
        return paramValue;
    }

    public static LocalDate getDateFromRequestParam(Request req, DateTimeFormatter expectDateFormat, String expectedDatePattern, String paramName) {
        LocalDate date = null;
        String param = null;
        try {
            param = HttpUtils.getParamFromRequest(req, paramName);
            date = LocalDate.parse(param, expectDateFormat);
        } catch (DateTimeParseException e) {
            LOG.error("Unable to parse {} : {}.", paramName, param, e);
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, paramName + " value: " + param + " is not a valid date. Must be in the format: " + expectedDatePattern);
        }
        return date;
    }

}
