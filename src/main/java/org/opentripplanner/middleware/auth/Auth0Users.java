package org.opentripplanner.middleware.auth;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.auth0.json.mgmt.users.UsersPage;
import com.auth0.net.AuthRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * This class contains methods for querying Auth0 users using the Auth0 User Management API. Auth0 docs describing the
 * searchable fields and query syntax are here: https://auth0.com/docs/api/management/v2/user-search
 */
public class Auth0Users {
    private static final String AUTH0_DOMAIN = getConfigPropertyAsText("AUTH0_DOMAIN");
    // This client/secret pair is for making requests for an API access token used with the Management API.
    private static final String AUTH0_API_CLIENT = getConfigPropertyAsText("AUTH0_API_CLIENT");
    private static final String AUTH0_API_SECRET = getConfigPropertyAsText("AUTH0_API_SECRET");
    // This is the UI client ID which is currently used to synchronize the user permissions object between server and UI.
    private static final String clientId = getConfigPropertyAsText("AUTH0_CLIENT_ID");
    private static final String MANAGEMENT_API_VERSION = "v2";
    private static final String SEARCH_API_VERSION = "v3";
    public static final String API_PATH = "/api/" + MANAGEMENT_API_VERSION;
    public static final String USERS_API_PATH = API_PATH + "/users";
    // Cached API token so that we do not have to request a new one each time a Management API request is made.
    private static TokenHolder cachedToken = null;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Users.class);
    private static final AuthAPI authAPI = new AuthAPI(AUTH0_DOMAIN, AUTH0_API_CLIENT, AUTH0_API_SECRET);

    /**
     * Assign Auth0 admin role to the users specified by the provided user IDs. In order to restrict access to the
     * Admin Dashboard to only those Auth0 users designated as admins, the admin role must be assigned when an admin
     * user is created (or if the Auth0 user already exists, the admin role can be assigned to their pre-existing user
     * profile).
     */
    public static boolean assignAdminRoleToUser(String... userIds) {
        String apiToken = getApiToken();
        if (AUTH0_DOMAIN == null || apiToken == null) {
            LOG.error("Cannot call Management API because token or Auth0 domain is null.");
            return false;
        }
        String adminRoleId = getConfigPropertyAsText("AUTH0_ROLE");
        ManagementAPI mgmt = new ManagementAPI(AUTH0_DOMAIN, apiToken);
        try {
            mgmt.roles()
                .assignUsers(adminRoleId, List.of(userIds))
                .execute();
            return true;
        } catch (Auth0Exception e) {
            LOG.error("Could not assign users {} to role {}", userIds, adminRoleId, e);
            return false;
        }
    }

    /**
     * Gets an Auth0 API access token for authenticating requests to the Auth0 Management API. This will either create
     * a new token using the oauth token endpoint or grab a cached token that it has already created (if it has not
     * expired). More information on setting this up is here: https://auth0.com/docs/api/management/v2/get-access-tokens-for-production
     */
    public static String getApiToken() {
        long nowInMillis = new Date().getTime();
        // If cached token has not expired, use it instead of requesting a new one.
        if (cachedToken != null && cachedToken.getExpiresIn() > 60) {
            long minutesToExpiration = cachedToken.getExpiresIn() / 60;
            LOG.info("Using cached token (expires in {} minutes)", minutesToExpiration);
            return cachedToken.getAccessToken();
        }
        LOG.info("Getting new Auth0 API access token (cached token does not exist or has expired).");
        AuthRequest tokenRequest = authAPI.requestToken(getAuth0Url() + API_PATH + "/");
        // Cache token for later use and return token string.
        try {
            cachedToken = tokenRequest.execute();
        } catch (Auth0Exception e) {
            LOG.error("Could not fetch Auth0 token", e);
            return null;
        }
        return cachedToken.getAccessToken();
    }

    /**
     * Wrapper method for performing user search with default per page count.
     * @return JSON string of users matching search query
     */
    public static UsersPage getAuth0Users(String searchQuery, int page) {
        String apiToken = getApiToken();
        if (AUTH0_DOMAIN == null || apiToken == null) {
            LOG.error("Cannot call Management API because token or Auth0 domain is null.");
            return null;
        }
        UserFilter filter = new UserFilter()
            .withQuery(searchQuery)
            .withPage(page, 10)
            .withSearchEngine(SEARCH_API_VERSION)
            .withTotals(false);
        ManagementAPI mgmt = new ManagementAPI(AUTH0_DOMAIN, apiToken);
        try {
            return mgmt.users().list(filter).execute();
        } catch (Auth0Exception e) {
            LOG.error("Could not fetch users for query.", e);
            return null;
        }
    }

    /**
     * Wrapper method for performing user search with default per page count and page number = 0.
     */
    public static UsersPage getAuth0Users(String queryString) {
        return getAuth0Users(queryString, 0);
    }

    /**
     * Get a single Auth0 user for the specified email.
     */
    public static Auth0UserProfile getUserByEmail(String email) {
        ManagementAPI mgmt = new ManagementAPI(AUTH0_DOMAIN, getApiToken());
        try {
            List<User> users = mgmt.users().listByEmail(email, null).execute();
            if (users.size() > 0) return new Auth0UserProfile(users.get(0));
        } catch (Auth0Exception e) {
            LOG.error("Could not perform user search by email", e);
        }
        return null;
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

    /**
     * Get number of users for the application.
     */
    public static int getAuth0UserCount(String searchQuery) {
        String apiToken = getApiToken();
        if (AUTH0_DOMAIN == null || apiToken == null) {
            LOG.error("Cannot call Management API because token or Auth0 domain is null.");
            return -1;
        }
        ManagementAPI mgmt = new ManagementAPI(AUTH0_DOMAIN, apiToken);
        UserFilter filter = new UserFilter()
            .withQuery(searchQuery)
            .withSearchEngine(SEARCH_API_VERSION)
            .withTotals(true);
        try {
            UsersPage page = mgmt.users().list(filter).execute();
            return page.getTotal();
        } catch (Auth0Exception e) {
            LOG.error("Could not fetch user count.", e);
            return -1;
        }
    }
}
