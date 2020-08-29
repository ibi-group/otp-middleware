package org.opentripplanner.middleware.models;

import com.amazonaws.services.apigateway.model.CreateApiKeyResult;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a subset of an AWS API Gateway API key.
 */
public class ApiKey implements Serializable {

    /**
     * The api key id as provided by AWS API Gateway.
     */
    public String keyId;

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
        keyId = apiKeyId;
    }

    /**
     * Construct ApiKey from AWS api gateway create api key result.
     */
    public ApiKey(CreateApiKeyResult apiKeyResult) {
        keyId = apiKeyResult.getId();
        name = apiKeyResult.getName();
        value = apiKeyResult.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKey apiKey = (ApiKey) o;
        return keyId.equals(apiKey.keyId) &&
            name.equals(apiKey.name) &&
            value.equals(apiKey.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, name, value);
    }
}
