package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.HaltException;
import spark.Request;

import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link AbstractUserController} for {@link ApiUser}.
 */
public class ApiUserController extends AbstractUserController<ApiUser> {
    private static final String DEFAULT_USAGE_PLAN_ID
        = getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");

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
        CreateApiKeyResult apiKey = ApiGatewayUtils.createApiKey(user.id, DEFAULT_USAGE_PLAN_ID);
        if (apiKey == null) {
            logMessageAndHalt(req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Unable to get AWS api key for user %s", user),
                null);
        }

        // store api key id (not the actual api key)
        user.apiKeyIds.add(apiKey.getId());

        try {
            return super.preCreateHook(user, req);
        } catch (HaltException e) {
            ApiGatewayUtils.deleteApiKeys(user.apiKeyIds);
            throw e;
        }
    }

    /**
     * Before deleting an API user in MongoDB,
     * remove the API keys for this user from AWS.
     */
    @Override
    boolean preDeleteHook(ApiUser user, Request req) {
        ApiGatewayUtils.deleteApiKeys(user.apiKeyIds);
        return true;
    }

}
