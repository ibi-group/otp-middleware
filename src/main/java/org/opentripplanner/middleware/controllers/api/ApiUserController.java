package org.opentripplanner.middleware.controllers.api;

import com.amazonaws.services.apigateway.model.CreateApiKeyResult;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.HaltException;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.OtpMiddlewareMain.getConfigPropertyAsText;
import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;
import static org.opentripplanner.middleware.utils.ApiGatewayUtils.deleteApiKey;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link AbstractUserController} for {@link ApiUser}. This controller also contains methods for
 * managing an {@link ApiUser}'s API keys.
 */
public class ApiUserController extends AbstractUserController<ApiUser> {
    private static final String DEFAULT_USAGE_PLAN_ID = getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");
    private static final String API_KEY_PATH = "/apikey";
    private static final int API_KEY_LIMIT_PER_USER = 2;
    private static final String API_KEY_ID_PARAM = "/:apiKeyId";

    public ApiUserController(String apiPrefix) {
        super(apiPrefix, Persistence.apiUsers, "secure/application");
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        LOG.info("Registering path {}.", ROOT_ROUTE + API_KEY_PATH);

        // Add the api key route BEFORE the regular CRUD methods
        ApiEndpoint modifiedEndpoint = baseEndpoint
            // Create API key
            .post(path(ID_PATH + API_KEY_PATH)
                    .withDescription("Creates API key for ApiUser (with optional AWS API Gateway usage plan ID).")
                    .withConsumes(JSON_ONLY)
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The user ID")
                    .and()
                    .withQueryParam().withName("usagePlanId").withRequired(false).withDescription("Optional AWS API Gateway usage plan ID.")
                    .and()
                    .withProduces(JSON_ONLY)
                    .withResponseType(persistence.clazz),
                this::createApiKeyForApiUser, JsonUtils::toJson
            )
            // Delete API key
            .delete(path(ID_PATH + API_KEY_PATH + API_KEY_ID_PARAM)
                    .withDescription("Deletes API key for ApiUser.")
                    .withConsumes(JSON_ONLY)
                    .withPathParam().withName(ID_PARAM).withDescription("The user ID.")
                    .and()
                    .withPathParam().withName("apiKeyId").withDescription("The ID of the API key.")
                    .and()
                    .withProduces(JSON_ONLY)
                    .withResponseType(persistence.clazz),
                this::deleteApiKeyForApiUser, JsonUtils::toJson
            );


        // Add the regular CRUD methods after defining the /apikey route.
        super.buildEndpoint(modifiedEndpoint);
    }

    /**
     * Shorthand method to determine if an API user exists and has an API key.
     */
    private boolean userHasKey(ApiUser user, String apiKeyId) {
        return user != null &&
            user.apiKeys
                .stream()
                .anyMatch(apiKey -> apiKeyId.equals(apiKey.id));
    }

    /**
     * Create a new API key and assign it to the provided usage plan. If no usage plan is provided use the default
     * usage plan instead.
     */
    private ApiUser createApiKeyForApiUser(Request req, Response res) {
        ApiUser targetUser = getApiUser(req);
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        String usagePlanId = req.queryParamOrDefault("usagePlanId", DEFAULT_USAGE_PLAN_ID);
        // If requester is not an admin user, force the usage plan ID to the default and enforce key limit. A non-admin
        // user should not be able to create an API key for any usage plan.
        if (!isUserAdmin(requestingUser)) {
            usagePlanId = DEFAULT_USAGE_PLAN_ID;
            if (targetUser.apiKeys.size() >= API_KEY_LIMIT_PER_USER) {
                logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "User has reached API key limit.");
            }
        }
        // FIXME Should an Api user be limited to one api key per usage plan (and perhaps stage)?
        CreateApiKeyResult apiKeyResult = ApiGatewayUtils.createApiKey(targetUser.id, usagePlanId);
        if (apiKeyResult == null || apiKeyResult.getId() == null) {
            logMessageAndHalt(req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Unable to get AWS API key for user id (%s) and usage plan id (%s)", targetUser.id, usagePlanId),
                null
            );
            return null;
        }
        // Add new API key to user and persist
        targetUser.apiKeys.add(new ApiKey(apiKeyResult));
        Persistence.apiUsers.replace(targetUser.id, targetUser);
        return Persistence.apiUsers.getById(targetUser.id);
    }

    /**
     * Delete an api key from a given user's list of api keys (if present) and from AWS api gateway.
     */
    private ApiUser deleteApiKeyForApiUser(Request req, Response res) {
        ApiUser targetUser = getApiUser(req);
        String apiKeyId = HttpUtils.getRequiredParamFromRequest(req, "apiKeyId");
        if (apiKeyId == null) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                "An api key id is required",
                null);
        }
        if (!userHasKey(targetUser, apiKeyId)) {
            logMessageAndHalt(req,
                HttpStatus.NOT_FOUND_404,
                String.format("User id (%s) does not have expected api key id (%s)", targetUser.id, apiKeyId),
                null);
        }

        // Delete API key from AWS.
        boolean success = deleteApiKey(new ApiKey(apiKeyId));
        if (success) {
            // Delete api key from user and persist
            targetUser.apiKeys.removeIf(apiKey -> apiKeyId.equals(apiKey.id));
            Persistence.apiUsers.replace(targetUser.id, targetUser);
            return Persistence.apiUsers.getById(targetUser.id);
        } else {
            // Throw halt if API key deletion failed.
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, "Unknown error deleting API key.");
            return null;
        }
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
        CreateApiKeyResult apiKeyResult = ApiGatewayUtils.createApiKey(user.id, DEFAULT_USAGE_PLAN_ID);
        if (apiKeyResult == null) {
            logMessageAndHalt(req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Unable to get AWS api key for user %s", user),
                null);
        }

        ApiKey apiKey = new ApiKey(apiKeyResult);

        // store api key id including the actual api key (value)
        user.apiKeys.add(apiKey);

        try {
            return super.preCreateHook(user, req);
        } catch (HaltException e) {
            deleteApiKey(apiKey);
            throw e;
        }
    }

    /**
     * Before deleting an API user in MongoDB,
     * remove the API keys for this user from AWS.
     */
    @Override
    boolean preDeleteHook(ApiUser user, Request req) {
        for (ApiKey apiKey : user.apiKeys) {
            deleteApiKey(apiKey);
        }
        return true;
    }

    /**
     * Get an Api user from Mongo DB based on the provided user id. Make sure user is admin or managing self.
     */
    private static ApiUser getApiUser(Request req) {
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        String userId = HttpUtils.getRequiredParamFromRequest(req, ID_PARAM);
        ApiUser apiUser = Persistence.apiUsers.getById(userId);
        if (apiUser == null) {
            logMessageAndHalt(
                req,
                HttpStatus.NOT_FOUND_404,
                String.format("No Api user matching the given user id (%s)", userId),
                null
            );
        }

        if (!apiUser.canBeManagedBy(requestingUser)) {
            logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Must be an admin to perform this operation.");
        }

        return apiUser;
    }
}
