package org.opentripplanner.middleware.utils;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.ConflictException;
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
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.hasConfigProperty;

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
        long startTime = DateTimeUtils.currentTimeMillis();

        AmazonApiGatewayClientBuilder gatewayBuilder = AmazonApiGatewayClient.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            gatewayBuilder.withCredentials(new ProfileCredentialsProvider(getConfigPropertyAsText("AWS_PROFILE")));
        }
        AmazonApiGateway gateway = gatewayBuilder.build();

        LOG.debug("Connection to AWS api gateway took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return gateway;
    }

    /**
     * Request an API key from AWS api gateway and assign it to an existing usage plan.
     */
    public static ApiKey createApiKey(ApiUser user, String usagePlanId) throws CreateApiKeyException {
        if (user == null || user.id == null || usagePlanId == null) {
            throw new CreateApiKeyException("All required input parameters must be provided.");
        }
        long startTime = DateTimeUtils.currentTimeMillis();
        AmazonApiGateway gateway;
        CreateApiKeyResult apiKeyResult = null;
        GetUsagePlanResult usagePlanResult;
        String keyId = null;
        try {
            gateway = getAmazonApiGateway();
            // Before creating key, verify usage plan exists (if not an exception will be thrown and caught below).
            GetUsagePlanRequest usagePlanRequest = new GetUsagePlanRequest();
            usagePlanRequest.withUsagePlanId(usagePlanId);
            usagePlanResult = gateway.getUsagePlan(usagePlanRequest);
            // Construct key name in the form email-planname-shortId (e.g., user@email.com-Unlimited-2). Note: shortId is
            // not intended to be unique, just for a bit of differentiation in the AWS console.
            String shortId = UUID.randomUUID().toString().substring(0, 7);
            String keyName = String.join("-", user.email, usagePlanResult.getName(), shortId);
            // Create API key with descriptive fields (for tracing back to users).
            CreateApiKeyRequest apiKeyRequest = new CreateApiKeyRequest()
                //FIXME This may need to include stage key(s). Not sure what impact that places on the calling
                // services though?
                .withName(keyName)
                // TODO: On deleting am ApiUser, it might be worth doing a query on the userId tag to make sure the keys
                //  have been cleared.
                .withTags(Collections.singletonMap("userId", user.id))
                .withEnabled(true)
                .withSdkRequestTimeout(SDK_REQUEST_TIMEOUT);
            apiKeyResult = gateway.createApiKey(apiKeyRequest);
            if (apiKeyResult != null) keyId = apiKeyResult.getId();
            LOG.info("Created new API key {}", keyId);
            try {
                // Try to add the API key to the specified usage plan.
                CreateUsagePlanKeyRequest usagePlanKeyRequest = new CreateUsagePlanKeyRequest()
                    .withUsagePlanId(usagePlanResult.getId())
                    .withKeyId(keyId)
                    .withKeyType("API_KEY");
                gateway.createUsagePlanKey(usagePlanKeyRequest);
            } catch (ConflictException e) {
                // If a ConflictException is thrown by createUsagePlanKey, it should be OK. This just means the key
                // is already associated with the usage plan.
                LOG.warn("API key {} already subscribed to usage plan", keyId, e);
            }
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            CreateApiKeyException createApiKeyException = new CreateApiKeyException(user.id, usagePlanId, e);
            BugsnagReporter.reportErrorToBugsnag("Error creating API key", keyId, createApiKeyException);
            throw createApiKeyException;
        }
        // Finally return API key if an unknown exception is not encountered.
        LOG.debug("Get api key and assign to usage plan took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return new ApiKey(apiKeyResult);
    }

    /**
     * Delete an API key from AWS API gateway.
     */
    public static boolean deleteApiKey(ApiKey apiKey) {
        long startTime = DateTimeUtils.currentTimeMillis();
        AmazonApiGateway gateway = getAmazonApiGateway();
        boolean success = true;
        try {
            DeleteApiKeyRequest deleteApiKeyRequest = new DeleteApiKeyRequest();
            deleteApiKeyRequest.setSdkRequestTimeout(SDK_REQUEST_TIMEOUT);
            deleteApiKeyRequest.setApiKey(apiKey.keyId);
            gateway.deleteApiKey(deleteApiKeyRequest);
            LOG.info("Deleting Api key {} took {} msec", apiKey.keyId, DateTimeUtils.currentTimeMillis() - startTime);
        } catch (NotFoundException e) {
            LOG.warn("Api key ({}) not found, unable to delete", apiKey.keyId, e);
        } catch (Exception e) {
            String message = String.format("Unable to delete api key (%s)", apiKey.keyId);
            BugsnagReporter.reportErrorToBugsnag(message, e);
            success = false;
        }
        return success;
    }

    /**
     * Get usage logs from AWS api gateway for a given key id, start and end date. Note: a null key id will return usage
     * for all usage plans and API keys.
     */
    public static List<GetUsageResult> getUsageLogsForKey(String keyId, String startDate, String endDate) {
        long startTime = DateTimeUtils.currentTimeMillis();

        AmazonApiGateway gateway = getAmazonApiGateway();

        GetUsagePlansRequest getUsagePlansRequest = new GetUsagePlansRequest();
        GetUsagePlansResult usagePlansResult = gateway.getUsagePlans(getUsagePlansRequest);
        List<GetUsageResult> usageResults = new ArrayList<>();
        for (UsagePlan usagePlan : usagePlansResult.getItems()) {
            GetUsageRequest getUsageRequest = new GetUsageRequest()
                .withKeyId(keyId)
                .withStartDate(startDate)
                .withEndDate(endDate)
                .withUsagePlanId(usagePlan.getId());
            try {
                usageResults.add(gateway.getUsage(getUsageRequest));
            } catch (Exception e) {
                // Catch any issues with bad request parameters (e.g., invalid API keyId or bad date format).
                String message = String.format("Unable to get usage results for key id (%s) between (%s) and (%s)",
                    keyId,
                    startDate,
                    endDate);
                BugsnagReporter.reportErrorToBugsnag(message, e);
                throw e;
            }
        }
        LOG.debug("Retrieving usage logs for api key took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return usageResults;
    }

    /**
     * Get usage logs from AWS api gateway for a given list of api keys, start and end date
     */
    public static List<GetUsageResult> getUsageLogsForKeys(List<ApiKey> apiKeys, String startDate, String endDate) {
        long startTime = DateTimeUtils.currentTimeMillis();

        List<GetUsageResult> usageResults = new ArrayList<>();
        for (ApiKey apiKey : apiKeys) {
            usageResults.addAll(getUsageLogsForKey(apiKey.keyId, startDate, endDate));
        }

        LOG.debug("Retrieving usage logs for a list of api keys took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return usageResults;
    }
}
