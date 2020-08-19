package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;
import static org.opentripplanner.middleware.utils.DateUtils.YYYY_MM_DD;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController implements Endpoint {
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
            HttpUtils.NO_FILTER
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
        List<ApiKey> apiKeys = getApiKeyIdsFromRequest(req);
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        if (!isUserAdmin(requestingUser)) {
            if (requestingUser.apiUser == null) {
                logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Action is not permitted for user.");
            }
            apiKeys = requestingUser.apiUser.apiKeys;
        }
        LocalDateTime now = LocalDateTime.now();
        // TODO: Future work might modify this so that we accept multiple API key IDs for a single request (depends on
        //  how third party developer accounts are structured).
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD);
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));
        try {
            if (apiKeys.isEmpty()) {
                // keyId param is optional (if not provided, all API keys will be included in response).
                return ApiGatewayUtils.getUsageLogsForKey(null, startDate, endDate);
            }
            return ApiGatewayUtils.getUsageLogsForKeys(apiKeys, startDate, endDate);
        } catch (Exception e) {
            // Catch any issues with bad request parameters (e.g., invalid API keyId or bad date format).
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Error requesting usage results", e);
        }

        return null;
    }

    /**
     * Extract the key ids from request, if present, an create a list of ApiKey objects
     */
    private static List<ApiKey> getApiKeyIdsFromRequest(Request req) {
        List<ApiKey> apiKeys = new ArrayList<>();
        String keyIdParam = req.queryParamOrDefault("keyId", "");
        if (keyIdParam.isEmpty()) {
            return apiKeys;
        }

        for (String keyId : keyIdParam.split(",")) {
            apiKeys.add(new ApiKey(keyId));
        }

        return apiKeys;
    }
}
