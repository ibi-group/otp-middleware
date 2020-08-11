package org.opentripplanner.middleware.models;

import com.amazonaws.services.apigateway.model.CreateApiKeyResult;

/**
 * Represents a subset of an AWS API Gateway API key.
 */
public class ApiKey {

    /**
     * The api key id as provided by AWS API Gateway.
     */
    public String id;

    /**
     * The name given to the api key.
     */
    public String name;

    /**
     * The api key value as provided by AWS API Gateway.
     */
    public String value;

    /**
     * This no-arg constructor exists to make MongoDB happy.
     */
    public ApiKey() {
    }

    /**
     * Construct ApiKey from a single api key id.
     */
    public ApiKey(String apiKeyId) {
        id = apiKeyId;
    }

    /**
     * Construct ApiKey from AWS api gateway create api key result.
     */
    public ApiKey(CreateApiKeyResult apiKeyResult) {
        id = apiKeyResult.getId();
        name = apiKeyResult.getName();
        value = apiKeyResult.getValue();
    }
}
