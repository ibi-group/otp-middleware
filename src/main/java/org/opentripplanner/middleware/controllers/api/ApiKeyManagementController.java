package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import com.amazonaws.services.apigateway.model.GetUsageResult;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Sets up HTTP endpoints for managing api keys and viewing api key request logs.
 */
public class ApiKeyManagementController {

    private static TypedPersistence<ApiUser> apiUsers = Persistence.apiUsers;

    private static final String DEFAULT_USAGE_PLAN_ID
        = getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");

    // FIXME Required for testing, but once agreed could be hardcoded?
    public static String createApiKeyEndpoint;
    public static String deleteApiKeyEndpoint;
    public static String usageApiKeyEndpoint;

    /**
     * Register http endpoints with {@link spark.Spark} instance at the provided API prefix.
     */
    public static void register(Service spark, String apiPrefix) {
        createApiKeyEndpoint = apiPrefix + "admin/apikey/create";
        deleteApiKeyEndpoint = apiPrefix + "admin/apikey/delete";
        usageApiKeyEndpoint = apiPrefix + "admin/apikey/usage";

        // FIXME need to agree on endpoint (/admin or /secure or a mix) and endpoint names
        spark.get(createApiKeyEndpoint, ApiKeyManagementController::createApiKeyForApiUser, JsonUtils::toJson);
        spark.get(deleteApiKeyEndpoint, ApiKeyManagementController::deleteApiKeyForApiUser, JsonUtils::toJson);
        spark.get(usageApiKeyEndpoint, ApiKeyManagementController::getUsageLogsForApiUser, JsonUtils::toJson);
    }

    /**
     * Create a new API key and assign it to the provided usage plan. If no usage plan is provided use the default
     * usage plan instead.
     */
    private static CreateApiKeyResult createApiKeyForApiUser(Request req, Response res) {

        ApiUser user = getApiUser(req);

        String usagePlanId = req.queryParamOrDefault("usagePlanId", DEFAULT_USAGE_PLAN_ID);
        // FIXME Should an Api user be limited to one api key per usage plan (and perhaps stage)?
        CreateApiKeyResult apiKey = ApiGatewayUtils.createApiKey(user.id, usagePlanId);
        if (apiKey == null) {
            logMessageAndHalt(req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Unable to get AWS api key for user (%s)", user),
                null);
        }

        // add new api key to user and persist
        user.apiKeyIds.add(apiKey.getId());
        apiUsers.replace(user.id, user);

        // FIXME assumption is that the api key will be required by the calling service
        return apiKey;
    }

    /**
     * Delete an api key from a given user's list of api keys (if present) and from AWS api gateway.
     */
    private static boolean deleteApiKeyForApiUser(Request req, Response res) {

        ApiUser user = getApiUser(req);

        String apiKeyId = req.queryParamOrDefault("apiKeyId", null);
        if (apiKeyId == null) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                "An api key id is required",
                null);
        }

        // FIXME perhaps not required?
        if (!user.apiKeyIds.contains(apiKeyId)) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                String.format("User id (%s) does not have expected api key id (%s)", user.id, apiKeyId),
                null);
        }

        // delete api key from user and persist
        user.apiKeyIds.remove(apiKeyId);
        apiUsers.replace(user.id, user);

        // delete api key from AWS
        ApiGatewayUtils.deleteApiKeys(Collections.singletonList(apiKeyId));

        return true;
    }

    /**
     * Get API usage from AWS api gateway for a users api keys.
     */
    private static List<GetUsageResult> getUsageLogsForApiUser(Request req, Response res) {

        ApiUser user = getApiUser(req);

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));

        try {
            // FIXME the GetUsageResult does not include the api key id. If the api key id is required the response will
            //  need to be enhanced.
            return ApiGatewayUtils.getUsageLogs(user.apiKeyIds, startDate, endDate);
        } catch (Exception e) {
            // Catch any issues with bad request parameters (e.g. bad date format).
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Error requesting usage results", e);
        }

        return null;
    }

    /**
     * Get an Api user from Mongo DB based on the provided user id. If no user id is provided, or no matching Api user
     * can be found, respond and halt.
     */
    private static ApiUser getApiUser(Request req) {

        String userId = req.queryParamOrDefault("userId", null);
        if (userId == null) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                "A user id is required to create an api key",
                null);
        }

        ApiUser user = apiUsers.getById(userId);
        if (user == null) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                String.format("No Api user matching the given user id (%s)", userId),
                null);
        }

        return user;
    }
}
