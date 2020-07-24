package org.opentripplanner.middleware.aws;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateApiKeyRequest;
import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import com.amazonaws.services.apigateway.model.CreateUsagePlanKeyRequest;
import com.amazonaws.services.apigateway.model.DeleteApiKeyRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlanRequest;
import com.amazonaws.services.apigateway.model.GetUsagePlanResult;
import com.amazonaws.services.apigateway.model.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Manages all interactions with AWS api gateway.
 */
public class AwsApiGateway {

    private static final Logger LOG = LoggerFactory.getLogger(AwsApiGateway.class);
    private static final int SDK_REQUEST_TIMEOUT = 10 * 1000;
    private static final String AWS_API_GATEWAY_USAGE_PLAN_ID
        = getConfigPropertyAsText("AWS_API_GATEWAY_USAGE_PLAN_ID");

    /**
     * Create connection to AWS api gateway.
     */
    private static AmazonApiGateway getAmazonApiGateway() {
        AmazonApiGatewayClientBuilder gatewayBuilder = AmazonApiGatewayClient.builder();
        AmazonApiGateway gateway = gatewayBuilder.build();
        return gateway;
    }

    /**
     * Request an API key from AWS api gateway and assign it to an existing usage plan.
     */
    public static String getApiKeyId(String userId) {
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

            // get existing usage plan
            GetUsagePlanRequest usagePlanRequest = new GetUsagePlanRequest();
            usagePlanRequest.withUsagePlanId(AWS_API_GATEWAY_USAGE_PLAN_ID);
            GetUsagePlanResult usagePlanResult = gateway.getUsagePlan(usagePlanRequest);

            // add API key to usage plan
            CreateUsagePlanKeyRequest usagePlanKeyRequest = new CreateUsagePlanKeyRequest();
            usagePlanKeyRequest
                .withUsagePlanId(usagePlanResult.getId())
                .withKeyId(apiKeyResult.getId())
                .withKeyType("API_KEY");
            gateway.createUsagePlanKey(usagePlanKeyRequest);

            return apiKeyResult.getId();
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
}
