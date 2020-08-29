package org.opentripplanner.middleware.auth;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.jobs.Job;
import com.auth0.json.mgmt.users.User;
import com.auth0.net.AuthRequest;
import org.apache.commons.validator.routines.EmailValidator;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This class contains methods for querying Auth0 users using the Auth0 User Management API. Auth0 docs describing the
 * searchable fields and query syntax are here: https://auth0.com/docs/api/management/v2/user-search
 */
public class Auth0Users {
    private static final String AUTH0_DOMAIN = getConfigPropertyAsText("AUTH0_DOMAIN");
    // This client/secret pair is for making requests for an API access token used with the Management API.
    private static final String AUTH0_API_CLIENT = getConfigPropertyAsText("AUTH0_API_CLIENT");
    private static final String AUTH0_API_SECRET = getConfigPropertyAsText("AUTH0_API_SECRET");
    private static final String DEFAULT_CONNECTION_TYPE = "Username-Password-Authentication";
    private static final String MANAGEMENT_API_VERSION = "v2";
    private static final String SEARCH_API_VERSION = "v3";
    public static final String API_PATH = "/api/" + MANAGEMENT_API_VERSION;
    /**
     * Cached API token so that we do not have to request a new one each time a Management API request is made.
     */
    private static TokenCache cachedToken = null;
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Users.class);
    private static final AuthAPI authAPI = new AuthAPI(AUTH0_DOMAIN, AUTH0_API_CLIENT, AUTH0_API_SECRET);

    /**
     * Creates a standard user for the provided email address. Defaults to a random UUID password and connection type
     * of {@link #DEFAULT_CONNECTION_TYPE}.
     */
    public static User createAuth0UserForEmail(String email) throws Auth0Exception {
        // Create user object and assign properties.
        User user = new User();
        user.setEmail(email);
        // TODO set name? phone? other Auth0 properties?
        user.setPassword(UUID.randomUUID().toString());
        user.setConnection(DEFAULT_CONNECTION_TYPE);
        return getManagementAPI()
            .users()
            .create(user)
            .execute();
    }

    /**
     * Delete Auth0 user by Auth0 user ID using the Management API.
     */
    public static void deleteAuth0User(String userId) throws Auth0Exception {
        getManagementAPI()
            .users()
            .delete(userId)
            .execute();
    }

    public static void setCachedToken(TokenCache tokenCache) {
        cachedToken = tokenCache;
    }

    public static TokenCache getCachedToken() {
        return cachedToken;
    }

    /**
     * Gets an Auth0 API access token for authenticating requests to the Auth0 Management API. This will either create
     * a new token using the oauth token endpoint or grab a cached token that it has already created (if it has not
     * expired). More information on setting this up is here: https://auth0.com/docs/api/management/v2/get-access-tokens-for-production
     */
    public static String getApiToken() {
        // If cached token has not expired, use it instead of requesting a new one.
        if (cachedToken != null && !cachedToken.isStale()) {
            LOG.info("Using cached token (expires in {} minutes)", cachedToken.minutesUntilExpiration());
            return cachedToken.tokenHolder.getAccessToken();
        }
        LOG.info("Getting new Auth0 API access token (cached token does not exist or has expired).");
        AuthRequest tokenRequest = authAPI.requestToken(getAuth0Url() + API_PATH + "/");
        // Cache token for later use and return token string.
        try {
            setCachedToken(new TokenCache(tokenRequest.execute()));
        } catch (Auth0Exception e) {
            LOG.error("Could not fetch Auth0 token", e);
            return null;
        }
        return cachedToken.tokenHolder.getAccessToken();
    }

    /**
     * Get a single Auth0 user for the specified email.
     */
    public static User getUserByEmail(String email, boolean createIfNotExists) {
        try {
            List<User> users = getManagementAPI()
                .users()
                .listByEmail(email, null)
                .execute();
            if (users.size() > 0) return users.get(0);
        } catch (Auth0Exception e) {
            BugsnagReporter.reportErrorToBugsnag("Could not perform user search by email", e);
            return null;
        }
        if (createIfNotExists) {
            try {
                return createAuth0UserForEmail(email);
            } catch (Auth0Exception e) {
                LOG.error("Could not create user for email", e);
            }
        }
        return null;
    }

    /**
     * Method to trigger an Auth0 job to resend a verification email. Returns an Auth0 {@link Job} which can be
     * used to monitor the progress of the job (using job ID). Typically the verification email goes out pretty quickly
     * so there shouldn't be too much of a need to monitor the result.
     */
    public static Job resendVerificationEmail(String userId) {
        try {
            return getManagementAPI()
                .jobs()
                // FIXME: This may need to be the otp-admin client_id instead.
                .sendVerificationEmail(userId, AUTH0_API_CLIENT)
                .execute();
        } catch (Auth0Exception e) {
            BugsnagReporter.reportErrorToBugsnag("Could not send verification email", e);
            return null;
        }
    }

    /**
     * Checks if an Auth0 user is a Data Tools user. Note: this may need to change once Data Tools user structure
     * changes.
     */
    public static boolean isDataToolsUser(User auth0UserProfile) {
        if (auth0UserProfile == null) return false;
        Map<String, Object> appMetadata = auth0UserProfile.getAppMetadata();
        return appMetadata != null && appMetadata.containsKey("datatools");
    }

    public static <U extends AbstractUser> U updateAuthFieldsForUser(U user, User auth0UserProfile) {
        // If a user with email exists in Auth0, assign existing Auth0 ID to new user record in MongoDB. Also,
        // check if the user is a Data Tools user and assign value accordingly.
        user.auth0UserId = auth0UserProfile.getId();
        user.isDataToolsUser = isDataToolsUser(auth0UserProfile);
        return user;
    }

    /**
     * Wrapper method for getting a new instance of the Auth0 {@link ManagementAPI}
     */
    private static ManagementAPI getManagementAPI() {
        return new ManagementAPI(AUTH0_DOMAIN, getApiToken());
    }

    /**
     * Shorthand method for validating a new user and creating the user with Auth0.
     */
    public static <U extends AbstractUser> User createNewAuth0User(U user, Request req, TypedPersistence<U> userStore) {
        validateUser(user, req);
        // Ensure no user with email exists in MongoDB.
        U userWithEmail = userStore.getOneFiltered(eq("email", user.email));
        if (userWithEmail != null) {
            logMessageAndHalt(req, 400, "User with email already exists in database!");
        }
        // Check for pre-existing user in Auth0 and create if not exists.
        User auth0UserProfile = getUserByEmail(user.email, true);
        if (auth0UserProfile == null) {
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, "Error creating user for email " + user.email);
        }
        return auth0UserProfile;
    }

    /**
     * Validates a generic {@link User} to be used before creating or updating a user.
     */
    public static <U extends AbstractUser> void validateUser(U user, Request req) {
        if (!isValidEmail(user.email)) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Email address is invalid.");
        }
    }

    /**
     * Validates a generic {@link User} to be used before updating a user.
     */
    public static <U extends AbstractUser> void validateExistingUser(U user, U preExistingUser, Request req, TypedPersistence<U> userStore) {
        validateUser(user, req);
        // Verify that email address for user has not changed.
        // TODO: should we permit changing email addresses? This would require making an update to Auth0.
        if (!preExistingUser.email.equals(user.email)) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Cannot change user email address!");
        }
        // Verify that Auth0 ID for user has not changed.
        if (!preExistingUser.auth0UserId.equals(user.auth0UserId)) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Cannot change Auth0 ID!");
        }
    }

    private static boolean isValidEmail(String email) {
        return EmailValidator.getInstance().isValid(email);
    }

    /**
     * Get the Auth0 URL, which is dependent on whether a test environment is in effect.
     */
    private static String getAuth0Url() {
        // If testing, return Wiremock server. Otherwise, use live Auth0 domain.
        return "your-auth0-domain".equals(AUTH0_DOMAIN)
            ? "http://locahost:8089"
            : "https://" + AUTH0_DOMAIN;
    }
}
