package org.opentripplanner.middleware.utils;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateApiKeyRequest;
import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import com.amazonaws.services.apigateway.model.CreateUsagePlanKeyRequest;
import com.amazonaws.services.apigateway.model.DeleteApiKeyRequest;
import com.amazonaws.services.apigateway.model.GetApiKeyRequest;
import com.amazonaws.services.apigateway.model.GetApiKeyResult;
import com.amazonaws.services.apigateway.model.GetUsagePlanRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlanResult;
import com.amazonaws.services.apigateway.model.GetUsagePlansRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlansResult;
import com.amazonaws.services.apigateway.model.GetUsageRequest;
import com.amazonaws.services.apigateway.model.GetUsageResult;
import com.amazonaws.services.apigateway.model.NotFoundException;
import com.amazonaws.services.apigateway.model.UsagePlan;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
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
                //FIXME This may need to include stage key(s). Not sure what impact that places on the calling
                // services though?
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
            String message = String.format("Unable to get api key from AWS for user id (%s) and usage plan id (%s)",
                userId,
                usagePlanId);
            BugsnagReporter.reportErrorToBugsnag(message, e);
        } finally {
            LOG.debug("Get api key and assign to usage plan took {} msec", System.currentTimeMillis() - startTime);
        }
        return null;
    }

    public static GetApiKeyResult getApiKey(String apiKeyId) {
        AmazonApiGateway gateway = getAmazonApiGateway();
        try {
            GetApiKeyRequest getApiKeyRequest = new GetApiKeyRequest()
                .withApiKey(apiKeyId);
            GetApiKeyResult apiKey = gateway.getApiKey(getApiKeyRequest);
            LOG.info("API key: {}", apiKey.getValue());
            return apiKey;
        } catch (Exception e) {
            LOG.error("Error encountered while fetching API Key", e);
            return null;
        }
    }

    /**
     * Delete api keys from AWS api gateway.
     */
    public static boolean deleteApiKeys(List<String> apiKeyIds) {
        long startTime = System.currentTimeMillis();
        AmazonApiGateway gateway = getAmazonApiGateway();
        for (String apiKeyId : apiKeyIds) {
            try {
                DeleteApiKeyRequest deleteApiKeyRequest = new DeleteApiKeyRequest();
                deleteApiKeyRequest.setSdkRequestTimeout(SDK_REQUEST_TIMEOUT);
                deleteApiKeyRequest.setApiKey(apiKeyId);
                gateway.deleteApiKey(deleteApiKeyRequest);
                LOG.debug("Deleting Api keys took {} msec", System.currentTimeMillis() - startTime);
                return true;
            } catch (NotFoundException e) {
                LOG.warn("Api key ({}) not found, unable to delete", apiKeyId, e);
            } catch (Exception e) {
                String message = String.format("Unable to delete api key (%s)", apiKeyId);
                BugsnagReporter.reportErrorToBugsnag(message, e);
            }
        }
        return false;
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
                String message = String.format("Unable to get usage results for key id (%s) with start date (%s) and end date (%s)",
                    keyId,
                    startDate,
                    endDate);
                BugsnagReporter.reportErrorToBugsnag(message, e);
                throw e;
            }
        }
        LOG.debug("Retrieving usage logs an api key took {} msec", System.currentTimeMillis() - startTime);
        return usageResults;
    }

    /**
     * Get usage logs from AWS api gateway for a given list of api keys, start and end date
     */
    public static List<GetUsageResult> getUsageLogs(List<String> apiKeyIds, String startDate, String endDate) {
        long startTime = System.currentTimeMillis();

        List<GetUsageResult> usageResults = new ArrayList<>();
        for (String apiKeyId : apiKeyIds) {
            usageResults.addAll(getUsageLogs(apiKeyId, startDate, endDate));
        }

        LOG.debug("Retrieving usage logs for a list of api keys took {} msec", System.currentTimeMillis() - startTime);
        return usageResults;
    }
}
