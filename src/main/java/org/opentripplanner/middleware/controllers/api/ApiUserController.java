package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.aws.AwsApiGateway;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.HaltException;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link AbstractUserController} for {@link ApiUser}.
 */
public class ApiUserController extends AbstractUserController<ApiUser> {
    public ApiUserController(String apiPrefix) {
        super(apiPrefix, Persistence.apiUsers, "secure/application");
    }

    @Override
    protected ApiUser getUserProfile(Auth0UserProfile profile) {
        return profile.apiUser;
    }

    /**
     * Before creating/storing an API user in MongoDB,
     * add the API keys for this user.
     */
    @Override
    ApiUser preCreateHook(ApiUser user, Request req) {
        String apiKeyId = AwsApiGateway.getApiKeyId(user.id);
        if (apiKeyId == null) {
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Unable to get AWS api key for user %s", user)
                , null);
        }

        //FIXME This suggests many, where only one is returned?
        user.apiKeyIds.add(apiKeyId);

        try {
            return super.preCreateHook(user, req);
        } catch (HaltException e) {
            AwsApiGateway.deleteApiKeys(user.apiKeyIds);
            throw e;
        }
    }

    /**
     * Before deleting an API user in MongoDB,
     * remove the API keys for this user.
     */
    @Override
    boolean preDeleteHook(ApiUser user, Request req) {
        AwsApiGateway.deleteApiKeys(user.apiKeyIds);
        return true;
    }

}
