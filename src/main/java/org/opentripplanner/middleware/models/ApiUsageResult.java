package org.opentripplanner.middleware.models;

import com.amazonaws.services.apigateway.model.GetUsageResult;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApiUsageResult implements Serializable {
    public final GetUsageResult result;
    public final Map<String, ApiUser> apiUsers;

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
