package org.opentripplanner.middleware.utils;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateApiKeyRequest;
import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import com.amazonaws.services.apigateway.model.CreateUsagePlanKeyRequest;
import com.amazonaws.services.apigateway.model.DeleteApiKeyRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlanRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlanResult;
import com.amazonaws.services.apigateway.model.GetUsagePlansRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlansResult;
import com.amazonaws.services.apigateway.model.GetUsageRequest;
import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.amazonaws.services.apigateway.model.NotFoundException;
import com.amazonaws.services.apigateway.model.UsagePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.spark.Main.hasConfigProperty;

/**
 * Manages all interactions with AWS api gateway.
 */
public class ApiGatewayUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ApiGatewayUtils.class);
    private static final int SDK_REQUEST_TIMEOUT = 10 * 1000;

    /**
     * Create connection to AWS api gateway.
     */
    private static AmazonApiGateway getAmazonApiGateway() {
        long startTime = System.currentTimeMillis();

        AmazonApiGatewayClientBuilder gatewayBuilder = AmazonApiGatewayClient.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            gatewayBuilder.withCredentials(new ProfileCredentialsProvider(getConfigPropertyAsText("AWS_PROFILE")));
        }
        AmazonApiGateway gateway = gatewayBuilder.build();

        LOG.debug("Connection to AWS api gateway took {} msec", System.currentTimeMillis() - startTime);
        return gateway;
    }

    /**
     * Request an API key from AWS api gateway and assign it to an existing usage plan.
     */
    public static CreateApiKeyResult createApiKey(String userId, String usagePlanId) {
        long startTime = System.currentTimeMillis();

        try {

            AmazonApiGateway gateway = getAmazonApiGateway();

            // create API key
            CreateApiKeyRequest apiKeyRequest = new CreateApiKeyRequest();
            apiKeyRequest.setSdkRequestTimeout(SDK_REQUEST_TIMEOUT);
            apiKeyRequest
                .withName(userId)
                .withCustomerId(userId)
                .withEnabled(true);
            CreateApiKeyResult apiKeyResult = gateway.createApiKey(apiKeyRequest);

            // get usage plan
            GetUsagePlanRequest usagePlanRequest = new GetUsagePlanRequest();
            usagePlanRequest.withUsagePlanId(usagePlanId);
            GetUsagePlanResult usagePlanResult = gateway.getUsagePlan(usagePlanRequest);

            // add API key to usage plan
            CreateUsagePlanKeyRequest usagePlanKeyRequest = new CreateUsagePlanKeyRequest();
            usagePlanKeyRequest
                .withUsagePlanId(usagePlanResult.getId())
                .withKeyId(apiKeyResult.getId())
                .withKeyType("API_KEY");
            gateway.createUsagePlanKey(usagePlanKeyRequest);

            return apiKeyResult;
        } catch (Exception e) {
            LOG.error("Unable to get api key from AWS", e);
            //FIXME add bugsnag
        } finally {
            LOG.debug("Get api key and assign to usage plan took {} msec", System.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Delete api keys from AWS api gateway.
     */
    public static void deleteApiKeys(List<String> apiKeyIds) {
        long startTime = System.currentTimeMillis();

        AmazonApiGateway gateway = getAmazonApiGateway();

        for (String apiKeyId : apiKeyIds) {
            try {
                DeleteApiKeyRequest deleteApiKeyRequest = new DeleteApiKeyRequest();
                deleteApiKeyRequest.setSdkRequestTimeout(SDK_REQUEST_TIMEOUT);
                deleteApiKeyRequest.setApiKey(apiKeyId);
                gateway.deleteApiKey(deleteApiKeyRequest);
            } catch (NotFoundException e) {
                LOG.warn("Api key ({}) not found, unable to delete", apiKeyId, e);
            } catch (Exception e) {
                LOG.error("Unable to delete api key ({})", apiKeyId, e);
                //FIXME add bugsnag
            }
        }
        LOG.debug("Deleting Api keys took {} msec", System.currentTimeMillis() - startTime);
    }

    /**
     * Get usage logs from AWS api gateway for a given key id, start and end date
     */
    public static List<GetUsageResult> getUsageLogs(String keyId, String startDate, String endDate) {
        long startTime = System.currentTimeMillis();

        AmazonApiGateway gateway = getAmazonApiGateway();

        GetUsagePlansRequest getUsagePlansRequest = new GetUsagePlansRequest();
        GetUsagePlansResult usagePlansResult = gateway.getUsagePlans(getUsagePlansRequest);
        List<GetUsageResult> usageResults = new ArrayList<>();
        for (UsagePlan usagePlan : usagePlansResult.getItems()) {
            GetUsageRequest getUsageRequest = new GetUsageRequest()
                // TODO: Once third party dev accounts are fleshed out, this query param might go away?
                .withKeyId(keyId)
                .withStartDate(startDate)
                .withEndDate(endDate)
                .withUsagePlanId(usagePlan.getId());
            try {
                usageResults.add(gateway.getUsage(getUsageRequest));
            } catch (Exception e) {
                // Catch any issues with bad request parameters (e.g., invalid API keyId or bad date format).
                LOG.error("Unable to get usage results for key id ({}) with start date ({}) and end date ({})",
                    keyId,
                    startDate,
                    endDate,
                    e);
                //FIXME add bugsnag
                throw e;
            }
        }
        LOG.debug("Retrieving usage logs took {} msec", System.currentTimeMillis() - startTime);
        return usageResults;
    }
}
