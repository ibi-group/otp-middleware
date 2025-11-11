package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.rest.Endpoint;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.stats.DailyStats;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.persistence.TypedPersistence.filterByDateRange;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for processing trip history related requests provided by MOD UI.
 * To provide a response to the calling MOD UI in JSON based on the passed in parameters.
 */
public class DailyStatsController implements Endpoint {
    private static final String FROM_DATE_PARAM = "fromDate";
    private static final String TO_DATE_PARAM = "toDate";
    private final String ROOT_ROUTE;

    public DailyStatsController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/dailystats";
    }

    /**
     * Register the API endpoint and GET resource to get daily stats
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for retrieving daily stats."),
            HttpUtils.NO_FILTER
        ).get(
            path(ROOT_ROUTE)
                .withDescription("Gets a paginated list of daily stats.")
                .withQueryParam()
                .withName(FROM_DATE_PARAM)
                .withPattern(DEFAULT_DATE_FORMAT_PATTERN)
                .withDefaultValue("30 days prior to the current date")
                .withDescription(String.format(
                    "If specified, the earliest date (format %s) for which daily stats are retrieved.",
                    DEFAULT_DATE_FORMAT_PATTERN
                )).and()
                .withQueryParam()
                .withName(TO_DATE_PARAM)
                .withPattern(DEFAULT_DATE_FORMAT_PATTERN)
                .withDefaultValue("The current date")
                .withDescription(String.format(
                    "If specified, the latest date (format %s) for which daily stats are retrieved.",
                    DEFAULT_DATE_FORMAT_PATTERN
                )).and()
                .withProduces(JSON_ONLY)
                .withResponseType(DailyStats.class),
            DailyStatsController::getDailyStats, JsonUtils::toJson);
    }

    /**
     * Return daily stats based on provided parameters.
     * An authorized user (Auth0) and user id are required.
     */
    private static ResponseList<DailyStats> getDailyStats(Request request, Response response) {
        // Only admins can get the stats. (otherwise a halt is thrown).
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(request);
        if (!requestingUser.isAdmin()) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Action is not permitted for user.");
            return null;
        }

        // Get params from request (or use defaults).
        LocalDateTime now = DateTimeUtils.nowAsLocalDateTime();
        DateTimeFormatter formatter = DateTimeUtils.DEFAULT_DATE_FORMATTER;
        String paramFromDate = request.queryParamOrDefault(FROM_DATE_PARAM, formatter.format(now.minusDays(30)));
        String paramToDate = request.queryParamOrDefault(TO_DATE_PARAM, formatter.format(now));

        Date fromDate = HttpUtils.getDate(request, FROM_DATE_PARAM, paramFromDate, LocalTime.MIDNIGHT);
        Date toDate = HttpUtils.getDate(request, TO_DATE_PARAM, paramToDate, LocalTime.MAX);
        // Throw halt if the date params are bad.
        if (fromDate != null && toDate != null && toDate.before(fromDate)) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400,
                String.format("%s (%s) before %s (%s)", TO_DATE_PARAM, paramToDate, FROM_DATE_PARAM,
                    paramFromDate));
        }
        Bson filter = filterByDateRange("date", fromDate, toDate);
        return Persistence.dailyStats.getResponseList(filter, 0, 0);
    }
}
