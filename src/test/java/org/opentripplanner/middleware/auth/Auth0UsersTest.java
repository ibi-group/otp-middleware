package org.opentripplanner.middleware.auth;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.net.AuthRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.spark.Main;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Auth0UsersTest {

    static TokenHolder cachedToken = null;

    @BeforeAll
    public static void setUp() throws IOException {
        Main.loadConfig(new String[0]);
        AuthRequest tokenRequest = Auth0Users.getTokenRequest();
        cachedToken = tokenRequest.execute();
    }

    /**
     * Shows that TokenWrapper.getExpiresIn() is invariant, it is the duration of the token validity.
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
     * Computes a token expiration time.
     */
    @Test
    public void getTokenExpiration() {
        long validity = cachedToken.getExpiresIn(); // seconds
        long expiration = System.currentTimeMillis() + validity * 1000;
        assertEquals(expiration, Auth0Users.getTokenExpirationTime(cachedToken));
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
        assertFalse(Auth0Users.isStale(expirationTime1));
        assertTrue(Auth0Users.isStale(expirationTime2));
        assertTrue(Auth0Users.isStale(expirationTime3));
        assertTrue(Auth0Users.isStale(expirationTime4));
        assertFalse(Auth0Users.isStale(expirationTime5));
    }
}
