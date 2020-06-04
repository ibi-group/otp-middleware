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
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.security.interfaces.RSAPublicKey;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.spark.Main.hasConfigProperty;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This handles verifying the Auth0 token passed in the auth header (e.g., Authorization: Bearer MY_TOKEN of Spark HTTP
 * requests.
 */
public class Auth0Connection {
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Connection.class);
    private static JWTVerifier verifier;

    /**
     * Check the incoming API request for the user token (and verify it) and assign as the "user" attribute on the
     * incoming request object for use in downstream controllers.
     * @param req Spark request object
     */
    public static void checkUser(Request req) {
        LOG.debug("Checking auth");
        // TODO Add check for testing environment
        if (authDisabled()) {
            // If in a development or testing environment, assign a mock profile of an admin user to the request
            // attribute and skip authentication.
            addUserToRequest(req, Auth0UserProfile.createTestAdminUser());
            return;
        }
        String token = getTokenFromRequest(req);
        // Handle getting the verifier outside of the below verification try/catch, which is intended to catch issues
        // with the client request. (getVerifier has its own exception/halt handling).
        verifier = getVerifier(req, token);
        // Validate the JWT and cast into the user profile, which will be attached as an attribute on the request object
        // for downstream controllers to check permissions.
        try {
            DecodedJWT jwt = verifier.verify(token);
            Auth0UserProfile profile = new Auth0UserProfile(jwt);
            // The user attribute is used on the server side to check user permissions and does not have all of the
            // fields that the raw Auth0 profile string does.
            addUserToRequest(req, profile);
        } catch (JWTVerificationException e){
            // Invalid signature/claims
            logMessageAndHalt(req, 401, "Login failed to verify with our authorization provider.", e);
        } catch (Exception e) {
            LOG.warn("Login failed to verify with our authorization provider.", e);
            logMessageAndHalt(req, 401, "Could not verify user's token");
        }
    }

    /** Assign user to request and check that the user is ad admin. */
    public static void checkUserIsAdmin(Request req, Response res) {
        // Check auth token in request (and add user object to request).
        checkUser(req);
        // Check that user object is present and is admin.
        Auth0UserProfile user = Auth0Connection.getUserFromRequest(req);
        if (!isUserAdmin(user)) {
            logMessageAndHalt(
                req,
                HttpStatus.UNAUTHORIZED_401,
                "User is not authorized to perform administrative action"
            );
        }
    }

    /** Check if the incoming user is an admin user */
    public static boolean isUserAdmin(Auth0UserProfile user) {
        return user != null && user.adminUser != null;
    }

    /** Add user profile to Spark Request object */
    public static void addUserToRequest(Request req, Auth0UserProfile user) {
        req.attribute("user", user);
    }

    /** Get user profile from Spark Request object */
    public static Auth0UserProfile getUserFromRequest (Request req) {
        return (Auth0UserProfile) req.attribute("user");
    }

    /**
     * Extract JWT token from Spark HTTP request (in Authorization header).
     */
    private static String getTokenFromRequest(Request req) {
        // Check that auth header is present and formatted correctly (Authorization: Bearer [token]).
        final String authHeader = req.headers("Authorization");
        if (authHeader == null) {
            logMessageAndHalt(req, 401, "Authorization header is missing.");
        }
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

    /**
     * Check whether authentication has been disabled via the DISABLE_AUTH config variable.
     */
    public static boolean authDisabled() {
        return hasConfigProperty("DISABLE_AUTH") && "true".equals(getConfigPropertyAsText("DISABLE_AUTH"));
    }

    /**
     * Confirm that the user exists
     */
    private static Auth0UserProfile isValidUser(Request request) {

        Auth0UserProfile profile = getUserFromRequest(request);
        if (profile == null || (profile.adminUser == null  && profile.otpUser == null && profile.apiUser == null)) {
            logMessageAndHalt(request, HttpStatus.NOT_FOUND_404, "Unknown user.");
        }

        return profile;
    }

    /**
     * Confirm that the user's actions are on their items if not admin.
     */
    public static void isAuthorized(String userId, Request request) {

        Auth0UserProfile profile = isValidUser(request);

        // let admin do anything
        if (profile.adminUser != null) {
            return;
        }

        if (userId == null ||
            (profile.otpUser != null && !profile.otpUser.id.equalsIgnoreCase(userId)) ||
            (profile.apiUser != null && !profile.apiUser.id.equalsIgnoreCase(userId))) {

            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unauthorized access.");
        }
    }

}
