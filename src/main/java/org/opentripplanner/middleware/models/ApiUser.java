package org.opentripplanner.middleware.models;

import com.auth0.exception.Auth0Exception;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a third-party application developer, which has a set of AWS API Gateway API keys which they can use to
 * access otp-middleware's endpoints (as well as the geocoding and OTP endpoints).
 */
public class ApiUser extends AbstractUser {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUser.class);
    /** Holds the API keys assigned to the user. */
    public List<ApiKey> apiKeys = new ArrayList<>();

    /** The name of the application built by this user. */
    public String appName;

    /** The purpose of the application built by this user. */
    public String appPurpose;

    /** The URL of the application built by this user. */
    public String appUrl;

    /** The company or organization that this user belongs to. */
    public String company;

    // FIXME: Move this member to AbstractUser?
    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    // FIXME: Move this member to AbstractUser?
    /** The name of this user */
    public String name;

    /**
     * Delete API user's API keys (from AWS) and self (from Mongo).
     */
    @Override
    public boolean delete() {
        for (ApiKey apiKey : apiKeys) {
            if (!ApiGatewayUtils.deleteApiKey(apiKey)) {
                LOG.error("Could not delete API key for user {}. Aborting delete user.", apiKey.keyId);
                return false;
            }
        }
        try {
            super.delete();
        } catch (Auth0Exception e) {
            LOG.error("Could not delete Auth0 user. Aborting delete user.", e);
            return false;
        }
        return Persistence.apiUsers.removeById(this.id);
    }

    public boolean createApiKey(String usagePlanId, boolean persist) {
        ApiKey apiKey = ApiGatewayUtils.createApiKey(this.id, usagePlanId);
        if (apiKey != null) {
            apiKeys.add(apiKey);
            if (persist) Persistence.apiUsers.replace(this.id, this);
            return true;
        }
        return false;
    }

    /**
     * @return the first {@link ApiUser} found with an {@link ApiKey#keyId} in {@link #apiKeys} that matches the
     * provided apiKeyId.
     */
    public static ApiUser userForApiKey(String apiKeyId) {
        return Persistence.apiUsers.getOneFiltered(Filters.elemMatch("apiKeys", Filters.eq("keyId", apiKeyId)));
    }
}
