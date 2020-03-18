package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.GetUsageRequest;
import com.amazonaws.services.apigateway.model.GetUsageResult;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.spark.Main.hasConfigProperty;

/**
 * Sets up HTTP endpoints for getting logging and request summary information from AWS Cloudwatch and API Gateway.
 */
public class LogController {
    private static AmazonApiGateway gateway;
    public static void register (Service spark, String apiPrefix) {
        AmazonApiGatewayClientBuilder gatewayBuilder = AmazonApiGatewayClient.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            gatewayBuilder.withCredentials(new ProfileCredentialsProvider(getConfigPropertyAsText("AWS_PROFILE")));
        }
        gateway = gatewayBuilder.build();
        spark.get(apiPrefix + "/secure/logs", LogController::getUsageLogs, JsonUtils::toJson);
    }

    public static GetUsageResult getUsageLogs(Request req, Response res) {
        // TODO: how can we extract this usage plan ID? Perhaps this will just be stored along with the API key
        //  information in the database table containing info on third party apps.
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDate = req.queryParamOrDefault("startDate", formatter.format(now.minusDays(30)));
        String endDate = req.queryParamOrDefault("endDate", formatter.format(now));
        String usagePlanId = "ajjp1j";
        GetUsageRequest getUsageRequest = new GetUsageRequest()
            .withStartDate(startDate)
            .withEndDate(endDate)
            .withUsagePlanId(usagePlanId);
        GetUsageResult usage = gateway.getUsage(getUsageRequest);
        return usage;
    }
}
