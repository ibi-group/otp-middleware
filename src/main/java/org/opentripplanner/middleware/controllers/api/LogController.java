package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.GetApiKeyRequest;
import com.amazonaws.services.apigateway.model.GetApiKeyResult;
import com.amazonaws.services.apigateway.model.GetApiKeysRequest;
import com.amazonaws.services.apigateway.model.GetApiKeysResult;
import com.amazonaws.services.apigateway.model.GetUsagePlansRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlansResult;
import com.amazonaws.services.apigateway.model.GetUsageRequest;
import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.amazonaws.services.apigateway.model.UsagePlan;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.spark.Main.hasConfigProperty;
import static org.opentripplanner.middleware.spark.Main.inTestEnvironment;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController {
    private static final Logger LOG = LoggerFactory.getLogger(LogController.class);
    private static AmazonApiGateway gateway;
    public static void register (Service spark, String apiPrefix) {
        AmazonApiGatewayClientBuilder gatewayBuilder = AmazonApiGatewayClient.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            gatewayBuilder.withCredentials(new ProfileCredentialsProvider(getConfigPropertyAsText("AWS_PROFILE")));
        }
        if (!inTestEnvironment) {
            // FIXME Only build API Gateway client if not testing to avoid Travis issues with AWS credentials.
            gateway = gatewayBuilder.build();
        } else {
            LOG.warn("Not building API Gateway client.");
        }
        spark.get(apiPrefix + "/secure/logs", LogController::getUsageLogs, JsonUtils::toJson);
    }

    public static List<GetUsageResult> getUsageLogs(Request req, Response res) {
        // TODO: how can we extract this usage plan ID? Perhaps this will just be stored along with the API key
        //  information in the database table containing info on third party apps.
        GetApiKeysRequest getApiKeysRequest = new GetApiKeysRequest();
        GetApiKeysResult apiKeys = gateway.getApiKeys(getApiKeysRequest);
        apiKeys.getItems();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));
        GetUsagePlansRequest getUsagePlansRequest = new GetUsagePlansRequest();
        GetUsagePlansResult usagePlansResult = gateway.getUsagePlans(getUsagePlansRequest);
        List<GetUsageResult> usageResults = new ArrayList<>();
        for (UsagePlan usagePlan : usagePlansResult.getItems()) {
            GetUsageRequest getUsageRequest = new GetUsageRequest()
                .withStartDate(startDate)
                .withEndDate(endDate)
                .withUsagePlanId(usagePlan.getId());
            usageResults.add(gateway.getUsage(getUsageRequest));
        }
        return usageResults;
    }
}
