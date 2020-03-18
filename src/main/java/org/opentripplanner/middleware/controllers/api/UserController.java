package org.opentripplanner.middleware.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.auth.Auth0Users.USERS_API_PATH;
import static org.opentripplanner.middleware.auth.Auth0Users.getUserByEmail;
import static org.opentripplanner.middleware.auth.Auth0Users.getUserById;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public class UserController extends ApiController<User> {
    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);
    private static ObjectMapper mapper = new ObjectMapper();
    private static final String AUTH0_DOMAIN = getConfigPropertyAsText("AUTH0_DOMAIN");
    public static final int TEST_AUTH0_PORT = 8089;
    public static final String TEST_AUTH0_DOMAIN = String.format("localhost:%d", TEST_AUTH0_PORT);
    public static final String DEFAULT_BASE_USERS_URL = "https://" + AUTH0_DOMAIN  + USERS_API_PATH;
    /** Users URL uses Auth0 domain by default, but can be overridden with {@link #setBaseUsersUrl(String)} for testing. */
    private static String baseUsersUrl = DEFAULT_BASE_USERS_URL;

    public UserController(String apiPrefix){
        super(apiPrefix, Persistence.users);
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link User#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    User preCreateHook(User user, Request req) {
        validateUser(user, req);
        return user;
    }

    private User validateUser(User user, Request req) {
        // Ensure no user with email exists in MongoDB.
        User userWithEmail = Persistence.users.getOneFiltered(eq("email", user.email));
        if (userWithEmail != null) {
            logMessageAndHalt(req, 400, "User with email already exists in database!");
        }
        boolean isDataToolsUser = false;
        // Check for user in Auth0.
        Auth0UserProfile auth0UserProfile = getUserByEmail(user.email);
        if (auth0UserProfile != null) {
            // If a user with email exists in Auth0, assign Auth0 ID to new user record in MongoDB.
            // TODO: Check app_metadata on user profile?
            isDataToolsUser = true;
            LOG.warn("User {} already exists in Auth0. Storing new record in Mongo.", auth0UserProfile.email);
        } else {
            LOG.info("No user found in Auth0. Creating new one.");
            // Otherwise, create the Auth0 user.
            auth0UserProfile = createAuth0User(user, req);
        }
        LOG.info("Created user: {}", auth0UserProfile.user_id);
        user.auth0UserId = auth0UserProfile.user_id;
        user.isDataToolsUser = isDataToolsUser;
        return user;
    }

    @Override
    User preUpdateHook(User user, Request req) {
        // TODO: Prevent email address change, prevent auth0 id change,
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(User user, Request req) {
        HttpDelete deleteUserRequest = new HttpDelete(getUserIdUrl(user));
        setHeaders(req, deleteUserRequest);
        executeRequestAndGetResult(deleteUserRequest, req);
        return true;
    }

    /**
     * Safely parse the userId and create an Auth0 url.
     */
    private static String getUserIdUrl(User user) {
        return String.format(
            "%s/%s",
            baseUsersUrl,
            URLEncoder.encode(user.auth0UserId, UTF_8)
        );
    }

    /**
     * Safely parse the request body into a JsonNode.
     *
     * @param req The initating request that came into datatools-server
     */
    private static JsonNode parseJsonFromBody(Request req) {
        try {
            return mapper.readTree(req.body());
        } catch (IOException e) {
            logMessageAndHalt(req, 400, "Failed to parse request body", e);
            return null;
        }
    }

    /**
     * HTTP endpoint to create new Auth0 user for the application.
     * @return
     */
    private static Auth0UserProfile createAuth0User(User user, Request req) {
        HttpPost createUserRequest = new HttpPost(baseUsersUrl);
        setHeaders(req, createUserRequest);
        ObjectNode node = mapper.createObjectNode();
        node.put("connection", "Username-Password-Authentication");
        node.put("email", user.email);
        node.put("password", UUID.randomUUID().toString());
        setRequestEntityUsingJson(createUserRequest, node.toString(), req);

        try {
            return mapper.readValue(executeRequestAndGetResult(createUserRequest, req), Auth0UserProfile.class);
        } catch (JsonProcessingException e) {
            logMessageAndHalt(req, 500, "Could not construct Auth0 user from JSON", e);
        }
        return null;
    }

    /**
     * Safely set the HTTP request body with a json string.
     *
     * @param request the outgoing HTTP post request
     * @param json The json to set in the request body
     * @param req The initating request that came into datatools-server
     */
    private static void setRequestEntityUsingJson(HttpEntityEnclosingRequestBase request, String json, Request req) {
        HttpEntity entity = new ByteArrayEntity(json.getBytes(UTF_8));
        request.setEntity(entity);
    }

    /**
     * Set some common headers on the request, including the API access token, which must be obtained via token request
     * to Auth0.
     */
    private static void setHeaders(Request sparkRequest, HttpRequestBase auth0Request) {
        String apiToken = Auth0Users.getApiToken();
        if (apiToken == null) {
            logMessageAndHalt(
                sparkRequest,
                400,
                "Failed to obtain Auth0 API token for request"
            );
        }
        auth0Request.addHeader("Authorization", "Bearer " + apiToken);
        auth0Request.setHeader("Accept-Charset", String.valueOf(UTF_8));
        auth0Request.setHeader("Content-Type", "application/json");
    }

    /**
     * Executes and logs an outgoing HTTP request, makes sure it worked and then returns the
     * stringified response body.
     *
     * @param httpRequest The outgoing HTTP request
     * @param req The initating request that came into datatools-server
     */
    private static String executeRequestAndGetResult(HttpRequestBase httpRequest, Request req) {
        // execute outside http request
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = null;
        try {
            LOG.info("Making request: ({})", httpRequest.toString());
            response = client.execute(httpRequest);
        } catch (IOException e) {
            LOG.error("HTTP request failed: ({})", httpRequest.toString());
            logMessageAndHalt(
                req,
                500,
                "Failed to make external HTTP request.",
                e
            );
        }

        // parse response body if there is one
        HttpEntity entity = response.getEntity();
        String result = null;
        if (entity != null) {
            try {
                result = EntityUtils.toString(entity);
            } catch (IOException e) {
                logMessageAndHalt(
                    req,
                    500,
                    String.format(
                        "Failed to parse result of http request (%s).",
                        httpRequest.toString()
                    ),
                    e
                );
            }
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode >= 300) {
            LOG.error(
                "HTTP request returned error code >= 300: ({}). Body: {}",
                httpRequest.toString(),
                result != null ? result : ""
            );
            // attempt to parse auth0 response to respond with an error message
            String auth0Message = "An Auth0 error occurred";
            JsonNode jsonResponse = null;
            try {
                jsonResponse = mapper.readTree(result);
            } catch (IOException e) {
                LOG.warn("Could not parse json from auth0 error message. Body: {}", result != null ? result : "");
                e.printStackTrace();
            }

            if (jsonResponse != null && jsonResponse.has("message")) {
                auth0Message = String.format("%s: %s", auth0Message, jsonResponse.get("message").asText());
            }

            logMessageAndHalt(req, statusCode, auth0Message);
        }

        LOG.info("Successfully made request: ({})", httpRequest.toString());

        return result;
    }

    /**
     * Used to override the base url for making requests to Auth0. This is primarily used for testing purposes to set
     * the url to something that is stubbed with WireMock.
     */
    public static void setBaseUsersUrl (String url) {
        baseUsersUrl = url;
    }
}
