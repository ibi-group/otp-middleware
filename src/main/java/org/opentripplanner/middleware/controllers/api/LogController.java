package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.GetUsageResult;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController {

    /**
     * Register http endpoints with {@link spark.Spark} instance at the provided API prefix.
     */
    public static void register(Service spark, String apiPrefix) {
        spark.get(apiPrefix + "/secure/logs", LogController::getUsageLogs, JsonUtils::toJson);
    }

    /**
     * HTTP endpoint to return the usage (number of requests made/requests remaining) for the AWS API Gateway usage
     * plans. Defaults to the last 30 days for all API keys in the AWS account.
     */
    private static List<GetUsageResult> getUsageLogs(Request req, Response res) {
        // keyId param is optional (if not provided, all API keys will be included in response).
        List<ApiKey> apiKeys = getApiKeyIdsFromRequest(req);
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        if (!isUserAdmin(requestingUser)) {
            if (requestingUser.apiUser == null) {
                logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Action is not permitted for user.");
            }
            apiKeys = requestingUser.apiUser.apiKeys;
        }
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));
        try {
            if (apiKeys.isEmpty()) {
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
