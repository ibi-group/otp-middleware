package org.opentripplanner.middleware.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.controllers.api.ApiUserController;
import org.opentripplanner.middleware.controllers.api.OtpUserController;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.security.interfaces.RSAPublicKey;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.hasConfigProperty;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This handles verifying the Auth0 token passed in the auth header (e.g., Authorization: Bearer MY_TOKEN of Spark HTTP
 * requests.
 */
public class Auth0Connection {
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Connection.class);
    private static JWTVerifier verifier;
    /**
     * Whether authentication is disabled for the HTTP endpoints. This defaults to the value in the config file, but can
     * be overridden (e.g., in tests) with {@link #setAuthDisabled(boolean)}.
     */
    private static boolean authDisabled = getDefaultAuthDisabled();

    /**
     * Check the incoming API request for the user token (and verify it) and assign as the "user" attribute on the
     * incoming request object for use in downstream controllers.
     *
     * @param req Spark request object
     */
    public static void checkUser(Request req) {
        LOG.debug("Checking auth");
        // TODO Add check for testing environment
        if (isAuthDisabled()) {
            // If in a development or testing environment, assign a mock profile of an admin user to the request
            // attribute and skip authentication.
            addUserToRequest(req, RequestingUser.createTestUser(req));
            return;
        }
        // Admin and OTP users authenticated by Bearer token
        String token = getTokenFromRequest(req);
        // Handle getting the verifier outside of the below verification try/catch, which is intended to catch issues
        // with the client request. (getVerifier has its own exception/halt handling).
        verifier = getVerifier(req, token);
        // Validate the JWT and cast into the user profile, which will be attached as an attribute on the request object
        // for downstream controllers to check permissions.
        try {
            DecodedJWT jwt = verifier.verify(token);
            RequestingUser profile = new RequestingUser(jwt);
            if (!isValidUser(profile)) {
                if (isCreatingSelf(req, profile)) {
                    // If creating self, no user account is required (it does not exist yet!). Note: creating an
                    // admin user requires that the requester is an admin (checkUserIsAdmin must be passed), so this
                    // is not a concern for that method/controller.
                    LOG.info("New user is creating self. OK to proceed without existing user object for auth0UserId");
                } else {
                    // Otherwise, if no valid user is found, halt the request.
                    logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, "No user found in database associated with the provided auth token.");
                }
            }
            // The user attribute is used on the server side to check user permissions and does not have all of the
            // fields that the raw Auth0 profile string does.
            addUserToRequest(req, profile);
        } catch (JWTVerificationException e) {
            // Invalid signature/claims
            logMessageAndHalt(req, 401, "Login failed to verify with our authorization provider.", e);
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Login failed to verify with our authorization provider.", e);
            logMessageAndHalt(req, 401, "Could not verify user's token");
        }
    }

    /**
     * Check for POST requests that are creating an {@link AbstractUser} (a proxy for OTP/API users).
     */
    private static boolean isCreatingSelf(Request req, RequestingUser profile) {
        String uri = req.uri();
        String method = req.requestMethod();
        // Check that this is a POST request.
        if (method.equalsIgnoreCase("POST")) {
            // Next, check that an OtpUser or ApiUser is being created (an admin must rely on another admin to create
            // them).
            boolean creatingOtpUser = uri.endsWith(OtpUserController.OTP_USER_PATH);
            boolean creatingApiUser = uri.endsWith(ApiUserController.API_USER_PATH);
            if (creatingApiUser || creatingOtpUser) {
                // Get the correct user class depending on request path.
                Class<? extends AbstractUser> userClass = creatingApiUser ? ApiUser.class : OtpUser.class;
                try {
                    // Next, get the user object from the request body, verifying that the Auth0UserId matches between
                    // requester and the new user object.
                    AbstractUser user = JsonUtils.getPOJOFromRequestBody(req, userClass);
                    return profile.auth0UserId.equals(user.auth0UserId);
                } catch (JsonProcessingException e) {
                    LOG.warn("Could not parse user object from request.", e);
                }
            }
        }
        return false;
    }

    public static boolean isAuthHeaderPresent(Request req) {
        final String authHeader = req.headers("Authorization");
        return authHeader != null;
    }

    /**
     * Assign user to request and check that the user is an admin.
     */
    public static void checkUserIsAdmin(Request req, Response res) {
        // Check auth token in request (and add user object to request).
        checkUser(req);
        // Check that user object is present and is admin.
        RequestingUser user = Auth0Connection.getUserFromRequest(req);
        if (!user.isAdmin()) {
            logMessageAndHalt(
                req,
                HttpStatus.UNAUTHORIZED_401,
                "User is not authorized to perform administrative action"
            );
        }
    }

    /**
     * Check that the API key used in the incoming request is associated with the matching {@link ApiUser} (which is
     * determined from the Authorization header).
     */
    //FIXME: Move this check into existing auth checks so it would be carried out automatically prior to any
    // business logic. Consider edge cases where a user can be both an API user and OTP user.
    public static void ensureApiUserHasApiKey(Request req) {
        RequestingUser requestingUser = getUserFromRequest(req);
        String apiKeyValueFromHeader = req.headers("x-api-key");
        if (requestingUser.apiUser == null ||
            apiKeyValueFromHeader == null ||
            !requestingUser.apiUser.hasApiKeyValue(apiKeyValueFromHeader)) {
            // If API user not found, log message and halt.
            logMessageAndHalt(
                req,
                HttpStatus.FORBIDDEN_403,
                "API key not linked to an API user.");
        }
    }

    /**
     * Add user profile to Spark Request object
     */
    public static void addUserToRequest(Request req, RequestingUser user) {
        req.attribute("user", user);
    }

    /**
     * Get user profile from Spark Request object
     */
    public static RequestingUser getUserFromRequest(Request req) {
        return req.attribute("user");
    }

    /**
     * Extract JWT token from Spark HTTP request (in Authorization header).
     */
    private static String getTokenFromRequest(Request req) {
        if (!isAuthHeaderPresent(req)) {
            logMessageAndHalt(req, 401, "Authorization header is missing.");
        }

        // Check that auth header is present and formatted correctly (Authorization: Bearer [token]).
        final String authHeader = req.headers("Authorization");
        String[] parts = authHeader.split(" ");
        if (parts.length != 2 || !"bearer".equals(parts[0].toLowerCase())) {
            logMessageAndHalt(req, 401, String.format("Authorization header is malformed: %s", authHeader));
        }
        // Retrieve token from auth header.
        String token = parts[1];
        if (token == null) {
            logMessageAndHalt(req, 401, "Could not find authorization token");
        }
        return token;
    }

    /**
     * Get the reusable verifier constructing a new instance if it has not been instantiated yet. Note: this only
     * supports the RSA256 algorithm.
     */
    private static JWTVerifier getVerifier(Request req, String token) {
        if (verifier == null) {
            try {
                final String domain = "https://" + getConfigPropertyAsText("AUTH0_DOMAIN") + "/";
                JwkProvider provider = new UrlJwkProvider(domain);
                // Decode the token.
                DecodedJWT jwt = JWT.decode(token);
                // Get public key from provider.
                Jwk jwk = provider.get(jwt.getKeyId());
                RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();
                // Use RS256 algorithm to verify token (uses public key/.pem file).
                Algorithm algorithm = Algorithm.RSA256(publicKey, null);
                verifier = JWT.require(algorithm)
                    .withIssuer(domain)
                    // Account for issues with server time drift.
                    // See https://github.com/auth0/java-jwt/issues/268
                    .acceptLeeway(3)
                    .build();
            } catch (IllegalStateException | NullPointerException | JwkException e) {
                LOG.error("Auth0 verifier configured incorrectly.");
                logMessageAndHalt(req, 500, "Server authentication configured incorrectly.", e);
            }
        }
        return verifier;
    }

    public static boolean getDefaultAuthDisabled() {
        return hasConfigProperty("DISABLE_AUTH") &&
            "true".equals(getConfigPropertyAsText("DISABLE_AUTH"));
    }

    /**
     * Whether authentication is disabled for HTTP endpoints.
     */
    public static boolean isAuthDisabled() {
        return authDisabled;
    }

    /**
     * Override the current {@link #authDisabled} value. This is used principally for setting up test environments that
     * require auth to be disabled.
     */
    public static void setAuthDisabled(boolean authDisabled) {
        Auth0Connection.authDisabled = authDisabled;
    }

    /**
     * Restore default {@link #authDisabled} value. This is used principally for tearing down test environments that
     * require auth to be disabled.
     */
    public static void restoreDefaultAuthDisabled() {
        setAuthDisabled(getDefaultAuthDisabled());
    }

    /**
     * Confirm that the user exists in at least one of the MongoDB user collections.
     */
    private static boolean isValidUser(RequestingUser profile) {
        return profile != null && (profile.adminUser != null || profile.otpUser != null || profile.apiUser != null);
    }

    /**
     * Confirm that the user's actions are on their items if not admin. In the case of an Api user confirm that the
     * user's actions, on Otp users, are Otp users they created initially.
     */
    public static void isAuthorized(String userId, Request request) {
        RequestingUser requestingUser = getUserFromRequest(request);
        // let admin do anything
        if (requestingUser.adminUser != null) {
            return;
        }
        // If userId is defined, it must be set to a value associated with a user.
        if (userId != null) {
            if (requestingUser.otpUser != null && requestingUser.otpUser.id.equals(userId)) {
                // Otp user requesting their item.
                return;
            }
            if (requestingUser.isThirdPartyUser() && requestingUser.apiUser.id.equals(userId)) {
                // Api user requesting their item.
                return;
            }
            if (requestingUser.isThirdPartyUser()) {
                // Api user potentially requesting an item on behalf of an Otp user they created.
                OtpUser otpUser = Persistence.otpUsers.getById(userId);
                if (requestingUser.canManageEntity(otpUser)) {
                    return;
                }
            }
        }
        logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unauthorized access.");
    }
}
