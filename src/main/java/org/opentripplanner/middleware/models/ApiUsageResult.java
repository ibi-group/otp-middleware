package org.opentripplanner.middleware.models;

import com.amazonaws.services.apigateway.model.GetUsageResult;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a wrapper around the AWS {@link GetUsageResult} class so that API usage per API key can be returned by
 * {@link org.opentripplanner.middleware.controllers.api.LogController} along with a mapping of API key ID to the
 * particular user it belongs to (if any). Note: this is not persisted to MongoDB (it's intended only for transient
 * results refreshed from the AWS SDK on each request).
 */
public class ApiUsageResult implements Serializable {
    public final GetUsageResult result;
    public final Map<String, ApiUser> apiUsers;

    /**
     * Construct an instance from {@link GetUsageResult}. This iterates over the API key IDs found in the result and
     * assigns the first {@link ApiUser} found for each keyId (there should only be one user for each key ID).
     */
    public ApiUsageResult(GetUsageResult result) {
        this.result = result;
        // Map keyIds to their respective API users. This contains a null check for ApiUser because it's possible that
        // an API key exists, but is not assigned to a user, and we don't want to throw a NPE in this case.
        this.apiUsers = result.getItems().keySet().stream()
            .flatMap(keyId -> {
                ApiUser userForKey = ApiUser.userForApiKey(keyId);
                return userForKey != null ? Stream.of(Map.entry(keyId, userForKey)) : null;
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
