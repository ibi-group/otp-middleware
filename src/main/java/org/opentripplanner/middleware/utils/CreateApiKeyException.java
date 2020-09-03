package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.models.ApiUser;

/**
 * This is the exception for issues when creating an API key for an {@link ApiUser}.
 */
public class CreateApiKeyException extends Exception {
    public CreateApiKeyException(String userId, String usagePlanId, Exception cause) {
        super(
            String.format(
                "Unable to create API key for user id (%s) and usage plan id (%s) (cause: %s)",
                userId,
                usagePlanId,
                cause.toString()
            ),
            cause
        );
    }

    public CreateApiKeyException(String message) {
        super(message);
    }
}
