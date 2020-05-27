package org.opentripplanner.middleware.auth;

import com.auth0.json.auth.TokenHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Auth0UsersTest {

    private static final long TOKEN_DURATION_SECONDS = 86400;
    private static ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() throws IOException {
        // Construct simplified token POJO that just contains expiration duration in seconds (one day).
        ObjectNode fakeToken = mapper.createObjectNode();
        fakeToken.put("expires_in", TOKEN_DURATION_SECONDS);
        TokenHolder tokenHolder = mapper.treeToValue(fakeToken, TokenHolder.class);
        // Set new cached token.
        Auth0Users.setCachedToken(new TokenCache(tokenHolder));
    }

    /**
     * Checks that token is valid soon after creation and seconds until expiration does diminish.
     */
    @Test
    public void isTokenStale() {
        // Verify that token is not expired soon after it set.
        assertFalse(Auth0Users.getCachedToken().isTokenExpired());
        // Verify that seconds until expiration has decreased since first set.
        assertTrue(Auth0Users.getCachedToken().secondsUntilExpiration() < TOKEN_DURATION_SECONDS);
    }
}
