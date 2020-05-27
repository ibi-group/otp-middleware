package org.opentripplanner.middleware.auth;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Auth0ConnectionTest {

    static TokenHolder cachedToken = null;

    @BeforeAll
    public static void setUp() throws IOException {
        cachedToken = new ObjectMapper().readValue("{\"expires_in\": 86400}", TokenHolder.class);
    }

    /**
     * Shows that TokenWrapper.getExpiresIn() is invariant
     * (see https://github.com/auth0/auth0-java/blob/master/src/main/java/com/auth0/json/auth/TokenHolder.java)
     * it is the duration of the token validity.
     */
    @Test
    public void tokenExpiryIsActuallyTokenLifetime() throws Auth0Exception, InterruptedException {

        long expiration1 = cachedToken.getExpiresIn();
        System.out.println("Expiration1: " + expiration1);
        Thread.sleep(3000);
        long expiration2 = cachedToken.getExpiresIn();
        System.out.println("Expiration2: " + expiration2);

        assertEquals(expiration1, expiration2);
    }
    
    /**
     * Returns true if a token is stale (expired or about to expire).
     */
    @Test
    public void isTokenStale() {
        long expirationTime1 = System.currentTimeMillis() + 500000; // Future expiration.
        long expirationTime2 = System.currentTimeMillis() - 500000; // Past expiration.
        long expirationTime3 = System.currentTimeMillis() + 59200; // Expires within a minute (stale).
        long expirationTime4 = System.currentTimeMillis() + 60000; // Expires in a minute (stale).
        long expirationTime5 = System.currentTimeMillis() + 60300; // Just about, but not stale.
        assertFalse(Auth0Connection.isStale(expirationTime1));
        assertTrue(Auth0Connection.isStale(expirationTime2));
        assertTrue(Auth0Connection.isStale(expirationTime3));
        assertTrue(Auth0Connection.isStale(expirationTime4));
        assertFalse(Auth0Connection.isStale(expirationTime5));
    }
}
