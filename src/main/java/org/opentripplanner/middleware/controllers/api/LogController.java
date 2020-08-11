package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.DateUtils.YYYY_MM_DD;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController implements Endpoint {
    private static final Logger LOG = LoggerFactory.getLogger(LogController.class);
    private final String ROOT_ROUTE;

    public LogController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/logs";
    }

    /**
     * Register the API endpoint and GET resource to retrieve API usage logs
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for retrieving API logs from AWS."),
            (q, a) -> LOG.info("Received request for 'logs' Rest API")
        ).get(path(ROOT_ROUTE)
                .withDescription("Gets a list of all API usage logs.")
                .withQueryParam()
                    .withName("keyId")
                    .withDescription("If specified, restricts the search to the specified AWS API key ID.").and()
                .withQueryParam()
                    .withName("startDate")
                    .withPattern(YYYY_MM_DD)
                    .withDefaultValue("30 days prior to the current date")
                    .withDescription(String.format(
                        "If specified, the earliest date (format %s) for which usage logs are retrieved.", YYYY_MM_DD
                    )).and()
                .withQueryParam()
                    .withName("endDate")
                    .withPattern(YYYY_MM_DD)
                    .withDefaultValue("The current date")
                    .withDescription(String.format(
                        "If specified, the latest date (format %s) for which usage logs are retrieved.", YYYY_MM_DD
                    )).and()
                .withProduces(JSON_ONLY)
                // Note: unlike what the name suggests, withResponseAsCollection does not generate an array
                // as the return type for this method. (It does generate the type for that class nonetheless.)
                .withResponseAsCollection(GetUsageResult.class),
            LogController::getUsageLogs, JsonUtils::toJson);
    }

    /**
     * HTTP endpoint to return the usage (number of requests made/requests remaining) for the AWS API Gateway usage
     * plans. Defaults to the last 30 days for all API keys in the AWS account.
     */
    private static List<GetUsageResult> getUsageLogs(Request req, Response res) {
        // keyId param is optional (if not provided, all API keys will be included in response).
        String keyId = req.queryParamOrDefault("keyId", null);
        LocalDateTime now = LocalDateTime.now();
        // TODO: Future work might modify this so that we accept multiple API key IDs for a single request (depends on
        //  how third party developer accounts are structured).
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD);
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));
        try {
            return ApiGatewayUtils.getUsageLogs(keyId, startDate, endDate);
        } catch (Exception e) {
            // Catch any issues with bad request parameters (e.g., invalid API keyId or bad date format).
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Error requesting usage results", e);
        }

        return null;
    }
}
