package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.CreateApiKeyException;
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
        boolean auth0UserDeleted = super.delete();
        if (auth0UserDeleted) {
            return Persistence.apiUsers.removeById(this.id);
        } else {
            LOG.warn("Aborting user deletion for {}", this.email);
            return false;
        }
    }

    public void createApiKey(String usagePlanId, boolean persist) throws CreateApiKeyException {
        try {
            ApiKey apiKey = ApiGatewayUtils.createApiKey(this, usagePlanId);
            apiKeys.add(apiKey);
            if (persist) Persistence.apiUsers.replace(this.id, this);
        } catch (CreateApiKeyException e) {
            LOG.error("Could not create API key for user {}", email, e);
            throw e;
        }
    }

    /**
     * @return the first {@link ApiUser} found with an {@link ApiKey#keyId} in {@link #apiKeys} that matches the
     * provided apiKeyId.
     */
    public static ApiUser userForApiKey(String apiKeyId) {
        return Persistence.apiUsers.getOneFiltered(Filters.elemMatch("apiKeys", Filters.eq("keyId", apiKeyId)));
    }
}
