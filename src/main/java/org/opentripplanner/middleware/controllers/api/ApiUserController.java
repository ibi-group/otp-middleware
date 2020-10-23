package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.auth.TokenHolder;
import com.beerboy.ss.ApiEndpoint;
import com.beerboy.ss.descriptor.MethodDescriptor;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.ApiKey;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ApiGatewayUtils;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.CreateApiKeyException;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.net.http.HttpResponse;

/**
 * Implementation of the {@link AbstractUserController} for {@link ApiUser}. This controller also contains methods for
 * managing an {@link ApiUser}'s API keys.
 */
public class ApiUserController extends AbstractUserController<ApiUser> {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserController.class);
    public static final String DEFAULT_USAGE_PLAN_ID = ConfigUtils.getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");
    private static final String API_KEY_PATH = "/apikey";
    private static final String AUTHENTICATE_PATH = "/authenticate";
    private static final int API_KEY_LIMIT_PER_USER = 2;
    private static final String API_KEY_ID_PARAM = "/:apiKeyId";
    public static final String API_USER_PATH = "secure/application";
    private static final String USERNAME_PARAM = "username";
    private static final String PASSWORD_PARAM = "password";

    public ApiUserController(String apiPrefix) {
        super(apiPrefix, Persistence.apiUsers, API_USER_PATH);
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        // Add the api key route BEFORE the regular CRUD methods
        // (to avoid interference with "get API user by ID" route)
        LOG.info("Registering path {}.", ROOT_ROUTE + ID_PATH + API_KEY_PATH);
        ApiEndpoint modifiedEndpoint = baseEndpoint
            // Create API key
            .post(MethodDescriptor.path(ID_PATH + API_KEY_PATH)
                    .withDescription("Creates API key for ApiUser (with optional AWS API Gateway usage plan ID).")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The user ID")
                    .and()
                    .withQueryParam().withName("usagePlanId").withDescription("Optional AWS API Gateway usage plan ID.")
                    .and()
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponseType(persistence.clazz),
                this::createApiKeyForApiUser, JsonUtils::toJson
            )
            // Delete API key
            .delete(MethodDescriptor.path(ID_PATH + API_KEY_PATH + API_KEY_ID_PARAM)
                    .withDescription("Deletes API key for ApiUser.")
                    .withPathParam().withName(ID_PARAM).withDescription("The user ID.")
                    .and()
                    .withPathParam().withName("apiKeyId").withDescription("The ID of the API key.")
                    .and()
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponseType(persistence.clazz),
                this::deleteApiKeyForApiUser, JsonUtils::toJson)
            // Authenticate user with Auth0
            .post(MethodDescriptor.path(AUTHENTICATE_PATH)
                    .withDescription("Authenticates ApiUser with Auth0.")
                    .withPathParam().withName(ID_PARAM).withDescription("The user ID.").and()
                    .withQueryParam().withName(USERNAME_PARAM).withRequired(true)
                    .withDescription("Auth0 username (usually email address).").and()
                    .withQueryParam().withName(PASSWORD_PARAM).withRequired(true)
                    .withDescription("Auth0 password.").and()
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponseType(TokenHolder.class),
                this::authenticateAuth0User, JsonUtils::toJson
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
                .anyMatch(apiKey -> apiKeyId.equals(apiKey.keyId));
    }

    /**
     * Authenticate user with Auth0 based on username (email) and password. If successful, return the complete Auth0
     * token else log message and halt.
     */
    private TokenHolder authenticateAuth0User(Request req, Response res) {
        String username = HttpUtils.getQueryParamFromRequest(req, USERNAME_PARAM, false);
        // FIXME: Should this be encrypted?!
        String password = HttpUtils.getQueryParamFromRequest(req, PASSWORD_PARAM, false);
        HttpResponse<String> auth0TokenResponse = Auth0Users.getCompleteAuth0TokenResponse(username, password);
        if (auth0TokenResponse == null || auth0TokenResponse.statusCode() != HttpStatus.OK_200) {
            JsonUtils.logMessageAndHalt(req,
                auth0TokenResponse.statusCode(),
                String.format("Cannot obtain Auth0 token for user %s", username),
                null
            );
        }
        return JsonUtils.getPOJOFromJSON(auth0TokenResponse.body(), TokenHolder.class);
    }

    /**
     * Create a new API key and assign it to the provided usage plan. If no usage plan is provided use the default
     * usage plan instead.
     */
    private ApiUser createApiKeyForApiUser(Request req, Response res) {
        ApiUser targetUser = getApiUser(req);
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        String usagePlanId = req.queryParamOrDefault("usagePlanId", DEFAULT_USAGE_PLAN_ID);
        // If requester is not an admin user, force the usage plan ID to the default and enforce key limit. A non-admin
        // user should not be able to create an API key for any usage plan.
        if (!requestingUser.isAdmin()) {
            usagePlanId = DEFAULT_USAGE_PLAN_ID;
            if (targetUser.apiKeys.size() >= API_KEY_LIMIT_PER_USER) {
                JsonUtils.logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "User has reached API key limit.");
            }
        }
        // FIXME Should an Api user be limited to one api key per usage plan (and perhaps stage)?
        try {
            ApiKey apiKey = ApiGatewayUtils.createApiKey(targetUser, usagePlanId);
            // Add new API key to user and persist
            targetUser.apiKeys.add(apiKey);
            Persistence.apiUsers.replace(targetUser.id, targetUser);
        } catch (CreateApiKeyException e) {
            JsonUtils.logMessageAndHalt(req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error creating API key",
                e
            );
        }
        return Persistence.apiUsers.getById(targetUser.id);
    }

    /**
     * Delete an api key from a given user's list of api keys (if present) and from AWS api gateway.
     */
    private ApiUser deleteApiKeyForApiUser(Request req, Response res) {
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        // Do not permit key deletion unless user is an admin.
        if (!requestingUser.isAdmin()) {
            JsonUtils.logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Must be an admin to delete an API key.");
        }
        ApiUser targetUser = getApiUser(req);
        String apiKeyId = HttpUtils.getRequiredParamFromRequest(req, "apiKeyId");
        if (apiKeyId == null) {
            JsonUtils.logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                "An api key id is required",
                null);
        }
        if (!userHasKey(targetUser, apiKeyId)) {
            JsonUtils.logMessageAndHalt(req,
                HttpStatus.NOT_FOUND_404,
                String.format("User id (%s) does not have expected api key id (%s)", targetUser.id, apiKeyId),
                null);
        }

        // Delete API key from AWS.
        boolean success = ApiGatewayUtils.deleteApiKey(new ApiKey(apiKeyId));
        if (success) {
            // Delete api key from user and persist
            targetUser.apiKeys.removeIf(apiKey -> apiKeyId.equals(apiKey.keyId));
            Persistence.apiUsers.replace(targetUser.id, targetUser);
            return Persistence.apiUsers.getById(targetUser.id);
        } else {
            // Throw halt if API key deletion failed.
            JsonUtils.logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, "Unknown error deleting API key.");
            return null;
        }
    }

    @Override
    protected ApiUser getUserProfile(RequestingUser profile) {
        return profile.apiUser;
    }

    /**
     * Before creating/storing an API user in MongoDB,
     * add the API keys for this user.
     */
    @Override
    ApiUser preCreateHook(ApiUser user, Request req) {
        try {
            user.createApiKey(DEFAULT_USAGE_PLAN_ID, false);
        } catch (CreateApiKeyException e) {
            JsonUtils.logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Error creating API key",
                e
            );
            return null;
        }
        // Call AbstractUserController#preCreateHook and delete api key in case something goes wrong.
        try {
            return super.preCreateHook(user, req);
        } catch (HaltException e) {
            user.delete();
            throw e;
        }
    }

    /**
     * Before deleting an API user in MongoDB,
     * remove the API keys for this user from AWS.
     */
    @Override
    boolean preDeleteHook(ApiUser user, Request req) {
        // Note: API keys deleted in ApiUser#delete
        return true;
    }

    /**
     * Get an Api user from Mongo DB based on the provided user id. Make sure user is admin or managing self.
     */
    private static ApiUser getApiUser(Request req) {
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        String userId = HttpUtils.getRequiredParamFromRequest(req, ID_PARAM);
        ApiUser apiUser = Persistence.apiUsers.getById(userId);
        if (apiUser == null) {
            JsonUtils.logMessageAndHalt(
                req,
                HttpStatus.NOT_FOUND_404,
                String.format("No Api user matching the given user id (%s)", userId),
                null
            );
        }

        if (!apiUser.canBeManagedBy(requestingUser)) {
            JsonUtils.logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, "Must be an admin to perform this operation.");
        }
        return apiUser;
    }
}
