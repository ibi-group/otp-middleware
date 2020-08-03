package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.beerboy.ss.ApiEndpoint;
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
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController implements Endpoint {
    private static final Logger LOG = LoggerFactory.getLogger(LogController.class);
    private final Class clazz;
    private final String ROOT_ROUTE;

    public LogController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/logs";
        this.clazz = GetUsageResult.class;
    }

    /**
     * This method is called on each object deriving from Endpoint by {@link SparkSwagger}
     * to register endpoints and generate the swagger documentation skeleton.
     * Here, we just register the GET method under the provided API prefix path to retrieve log usage.
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription(String.format("Log controller with type:%s", clazz)),
            (q, a) -> LOG.info("Received request for 'logs' Rest API")
        );
        apiEndpoint
            // Important: Unlike what the method name suggests,
            // withResponseAsCollection does not generate an array of the specified class,
            // although it generates the type for that class in the swagger output.
            .get(path(ROOT_ROUTE).withResponseAsCollection(clazz),
                LogController::getUsageLogs, JsonUtils::toJson)

            // Options response for CORS
            .options(path(""), (req, res) -> "");
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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
