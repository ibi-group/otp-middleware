package org.opentripplanner.middleware.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;
import static org.opentripplanner.middleware.spark.Main.hasConfigProperty;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * This handles verifying the Auth0 token passed in the Auth header of Spark HTTP requests.
 *
 * Created by demory on 3/22/16.
 */

public class Auth0Connection {
    public static final String APP_METADATA = "app_metadata";
    public static final String USER_METADATA = "user_metadata";
    public static final String SCOPE = "http://datatools";
    public static final String SCOPED_APP_METADATA = String.join("/", SCOPE, APP_METADATA);
    public static final String SCOPED_USER_METADATA = String.join("/", SCOPE, USER_METADATA);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
            req.attribute("user", Auth0UserProfile.createTestAdminUser());
            return;
        }
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
            req.attribute("user", profile);
        } catch (JWTVerificationException e){
            // Invalid signature/claims
            logMessageAndHalt(req, 401, "Login failed to verify with our authorization provider.", e);
        } catch (Exception e) {
            LOG.warn("Login failed to verify with our authorization provider.", e);
            logMessageAndHalt(req, 401, "Could not verify user's token");
        }
    }

    /**
     * Choose the correct JWT verification algorithm (based on the values present in env.yml config) and get the
     * respective verifier.
     */
    private static JWTVerifier getVerifier(Request req, String token) {
        if (verifier == null) {
            try {
                // Get public key from provider.
                final String domain = "https://" + getConfigPropertyAsText("AUTH0_DOMAIN") + "/";
                JwkProvider provider = new UrlJwkProvider(domain);
                DecodedJWT jwt = JWT.decode(token);
                Jwk jwk = provider.get(jwt.getKeyId());
                // Use RS256 algorithm to verify token (uses public key/.pem file).
                Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
                verifier = JWT.require(algorithm)
                    .withIssuer(domain)
                    .build(); //Reusable verifier instance
            } catch (IllegalStateException | NullPointerException | JwkException e) {
                LOG.error("Auth0 verifier configured incorrectly.");
                logMessageAndHalt(req, 500, "Server authentication configured incorrectly.", e);
            }
        }
        return verifier;
    }

    /**
     * Handle mapping token values to the expected keys. This accounts for app_metadata and user_metadata that have been
     * scoped to conform with OIDC (i.e., how newer Auth0 accounts structure the user profile) as well as the user_id ->
     * sub mapping.
     */
    private static void remapTokenValues(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();
        // If token did not contain app_metadata or user_metadata, add the scoped values to the decoded token object.
        if (!claims.containsKey(APP_METADATA) && claims.containsKey(SCOPED_APP_METADATA)) {
            claims.put(APP_METADATA, claims.get(SCOPED_APP_METADATA));
        }
        if (!claims.containsKey(USER_METADATA) && claims.containsKey(SCOPED_USER_METADATA)) {
            claims.put(USER_METADATA, claims.get(SCOPED_USER_METADATA));
        }
        // Do the same for user_id -> sub
        if (!claims.containsKey("user_id") && claims.containsKey("sub")) {
            claims.put("user_id", claims.get("sub"));
        }
        // Remove scoped metadata objects to clean up user profile object.
        claims.remove(SCOPED_APP_METADATA);
        claims.remove(SCOPED_USER_METADATA);
    }

    /**
     * Check whether authentication has been disabled via the DISABLE_AUTH config variable.
     */
    public static boolean authDisabled() {
        return hasConfigProperty("DISABLE_AUTH") && "true".equals(getConfigPropertyAsText("DISABLE_AUTH"));
    }
}
