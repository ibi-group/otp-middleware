package org.opentripplanner.middleware.models;

import software.amazon.awssdk.services.apigateway.model.CreateApiKeyResponse;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a subset of an AWS API Gateway API key.
 */
public class ApiKeyDetails implements Serializable {

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
    public ApiKeyDetails() {
    }

    /**
     * Construct ApiKeyDetails from a single api key id.
     */
    public ApiKeyDetails(String apiKeyId) {
        keyId = apiKeyId;
    }

    /**
     * Construct ApiKeyDetails from AWS api gateway create api key result.
     */
    public ApiKeyDetails(CreateApiKeyResponse apiKeyDetails) {
        this.keyId = apiKeyDetails.id();
        this.name = apiKeyDetails.name();
        this.value = apiKeyDetails.value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiKeyDetails apiKeyDetails = (ApiKeyDetails) o;
        return keyId.equals(apiKeyDetails.keyId) &&
            name.equals(apiKeyDetails.name) &&
            value.equals(apiKeyDetails.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, name, value);
    }
}
