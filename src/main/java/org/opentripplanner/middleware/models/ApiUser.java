package org.opentripplanner.middleware.models;

import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.persistence.Persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a third-party application developer, which has a set of AWS API Gateway API keys which they can use to
 * access otp-middleware's endpoints (as well as the geocoding and OTP endpoints).
 */
public class ApiUser extends AbstractUser {
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
     * @return the first {@link ApiUser} found with an {@link ApiKey#keyId} in {@link #apiKeys} that matches the
     * provided apiKeyId.
     */
    public static ApiUser userForApiKey(String apiKeyId) {
        return Persistence.apiUsers.getOneFiltered(Filters.elemMatch("apiKeys", Filters.eq("keyId", apiKeyId)));
    }
}
