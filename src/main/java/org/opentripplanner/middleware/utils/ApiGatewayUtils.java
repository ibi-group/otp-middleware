package org.opentripplanner.middleware.utils;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayClientBuilder;
import software.amazon.awssdk.services.apigateway.model.ConflictException;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyResponse;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.DeleteApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsagePlanRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsagePlansRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsageRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsageResponse;
import software.amazon.awssdk.services.apigateway.model.NotFoundException;
import software.amazon.awssdk.services.apigateway.model.UsagePlan;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.ApiKeyDetails;
import org.opentripplanner.middleware.models.ApiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
    private static ApiGatewayClient getAmazonApiGateway() {
        long startTime = DateTimeUtils.currentTimeMillis();

        ApiGatewayClientBuilder gatewayBuilder = ApiGatewayClient.builder()
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(SDK_REQUEST_TIMEOUT))
                .build());
        if (hasConfigProperty("AWS_PROFILE")) {
            gatewayBuilder.credentialsProvider(
                ProfileCredentialsProvider.create(getConfigPropertyAsText("AWS_PROFILE"))
            );
        }
        ApiGatewayClient gateway = gatewayBuilder.build();

        LOG.debug("Connection to AWS api gateway took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return gateway;
    }

    /**
     * Request an API key from AWS api gateway and assign it to an existing usage plan.
     */
    public static ApiKeyDetails createApiKey(ApiUser user, String usagePlanId) throws CreateApiKeyException {
        if (user == null || user.id == null || usagePlanId == null) {
            throw new CreateApiKeyException("All required input parameters must be provided.");
        }
        long startTime = DateTimeUtils.currentTimeMillis();
        CreateApiKeyResponse apiKeyResult;
        String keyId = null;
        try (ApiGatewayClient gateway = getAmazonApiGateway()) {
            // Before creating key, verify usage plan exists (if not an exception will be thrown and caught below).
            var usagePlanResult = gateway.getUsagePlan(GetUsagePlanRequest.builder().usagePlanId(usagePlanId).build());
            // Construct key name in the form email-planname-shortId (e.g., user@email.com-Unlimited-2). Note: shortId is
            // not intended to be unique, just for a bit of differentiation in the AWS console.
            String shortId = UUID.randomUUID().toString().substring(0, 7);
            String keyName = String.join("-", user.email, usagePlanResult.name(), shortId);
            // Create API key with descriptive fields (for tracing back to users).
            apiKeyResult = gateway.createApiKey(CreateApiKeyRequest.builder()
                // FIXME This may need to include stage key(s). Not sure what impact that places on the calling
                // services though?
                .name(keyName)
                // TODO: On deleting an ApiUser, it might be worth doing a query on the userId tag to make sure the keys
                // have been cleared.
                .tags(Collections.singletonMap("userId", user.id))
                .enabled(true)
                .build());
            if (apiKeyResult != null) keyId = apiKeyResult.id();
            LOG.info("Created new API key {}", keyId);
            try {
                // Try to add the API key to the specified usage plan.
                gateway.createUsagePlanKey(CreateUsagePlanKeyRequest.builder()
                    .usagePlanId(usagePlanResult.id())
                    .keyId(keyId)
                    .keyType("API_KEY")
                    .build());
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
        return new ApiKeyDetails(apiKeyResult);
    }

    /**
     * Delete an API key from AWS API gateway.
     */
    public static boolean deleteApiKey(ApiKeyDetails apiKeyDetails) {
        long startTime = DateTimeUtils.currentTimeMillis();
        boolean success = true;
        try (ApiGatewayClient gateway = getAmazonApiGateway()) {
            gateway.deleteApiKey(DeleteApiKeyRequest.builder().apiKey(apiKeyDetails.keyId).build());
            LOG.info("Deleting Api key {} took {} msec", apiKeyDetails.keyId, DateTimeUtils.currentTimeMillis() - startTime);
        } catch (NotFoundException e) {
            LOG.warn("Api key ({}) not found, unable to delete", apiKeyDetails.keyId, e);
        } catch (Exception e) {
            String message = String.format("Unable to delete api key (%s)", apiKeyDetails.keyId);
            BugsnagReporter.reportErrorToBugsnag(message, e);
            success = false;
        }
        return success;
    }

    /**
     * Get usage logs from AWS api gateway for a given key id, start and end date. Note: a null key id will return usage
     * for all usage plans and API keys.
     */
    public static List<GetUsageResponse> getUsageLogsForKey(String keyId, String startDate, String endDate) {
        long startTime = DateTimeUtils.currentTimeMillis();

        List<GetUsageResponse> usageResults = new ArrayList<>();
        try (ApiGatewayClient gateway = getAmazonApiGateway()) {
            var usagePlansResult = gateway.getUsagePlans(GetUsagePlansRequest.builder().build());
            for (UsagePlan usagePlan : usagePlansResult.items()) {
                GetUsageRequest getUsageRequest = GetUsageRequest.builder()
                    .keyId(keyId)
                    .startDate(startDate)
                    .endDate(endDate)
                    .usagePlanId(usagePlan.id())
                    .build();
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
        }
        LOG.debug("Retrieving usage logs for api key took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return usageResults;
    }

    /**
     * Get usage logs from AWS api gateway for a given list of api keys, start and end date
     */
    public static List<GetUsageResponse> getUsageLogsForKeys(
        List<ApiKeyDetails> apiKeyDetails,
        String startDate,
        String endDate
    ) {
        long startTime = DateTimeUtils.currentTimeMillis();

        List<GetUsageResponse> usageResults = new ArrayList<>();
        for (ApiKeyDetails apiKeyDetail : apiKeyDetails) {
            usageResults.addAll(getUsageLogsForKey(apiKeyDetail.keyId, startDate, endDate));
        }

        LOG.debug("Retrieving usage logs for a list of api keys took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
        return usageResults;
    }
}
